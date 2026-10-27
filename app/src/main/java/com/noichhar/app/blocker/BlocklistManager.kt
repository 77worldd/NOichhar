package com.noichhar.app.blocker

import android.content.Context
import androidx.room.withTransaction
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.database.NOichharDatabase
import com.noichhar.app.database.entity.BlocklistEntry
import com.noichhar.app.util.Constants
import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class BlocklistManager(
    private val context: Context,
    private val database: NOichharDatabase
) {
    val domainBlocker = DomainBlocker()

    private val appContext = context.applicationContext
    private val dao = database.blocklistDao()
    private val preferences = NOichharApp.instance.preferenceManager
    private val parser = HostsFileParser()
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (initialized) return@withLock
            if (dao.getCountSync() == 0) loadDefaultBlocklist()
            loadFromDatabase()
            initialized = true
        }
    }

    private suspend fun loadDefaultBlocklist() {
        val entries = LinkedHashMap<String, BlocklistEntry>()
        val builtIn = readBuiltInRules()

        builtIn.allowed.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            entries[normalized.domain] = allowEntry(normalized.domain, normalized.wildcard, SOURCE_ESSENTIAL)
        }
        builtIn.blocked.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            entries.putIfAbsent(
                normalized.domain,
                blockEntry(normalized.domain, normalized.wildcard, SOURCE_DEFAULT)
            )
        }
        builtIn.dohBypass.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            entries.putIfAbsent(
                normalized.domain,
                blockEntry(normalized.domain, normalized.wildcard, SOURCE_DOH_BYPASS)
            )
        }

        chunkedInsert(entries.values.toList())
        Logger.i("Loaded ${entries.size} built-in filter entries")
    }

    suspend fun loadFromDatabase() = withContext(Dispatchers.IO) {
        val entries = dao.getAllSync()
        val rules = ArrayList<BlockRule>(entries.size)
        for (entry in entries) {
            if (entry.source == SOURCE_DOH_BYPASS && !preferences.blockDohBypass) continue
            val display = if (entry.isWildcard) "*.${entry.domain}" else entry.domain
            rules += BlockRule(
                domain = display,
                wildcard = entry.isWildcard,
                allow = entry.source == SOURCE_ALLOW ||
                    entry.source == SOURCE_ESSENTIAL ||
                    entry.source == SOURCE_ALLOW_USER
            )
        }
        domainBlocker.loadRules(rules)
    }

    suspend fun addCustomDomain(rawDomain: String): Boolean = withContext(Dispatchers.IO) {
        val rule = DomainValidator.normalize(rawDomain) ?: return@withContext false
        val entry = BlocklistEntry(
            domain = rule.domain,
            source = SOURCE_USER,
            isWildcard = rule.wildcard,
            isUserAdded = true
        )
        dao.insert(entry)
        domainBlocker.addDomain(DomainValidator.display(rule))
        true
    }

    suspend fun addCustomAllowDomain(rawDomain: String, includeSubdomains: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val rule = DomainValidator.normalize(rawDomain) ?: return@withContext false
        val wildcard = rule.wildcard || includeSubdomains
        val entry = BlocklistEntry(
            domain = rule.domain,
            source = SOURCE_ALLOW_USER,
            isWildcard = wildcard,
            isUserAdded = true
        )
        dao.insert(entry)
        loadFromDatabase()
        true
    }

    suspend fun removeDomain(rawDomain: String) = withContext(Dispatchers.IO) {
        val rule = DomainValidator.normalize(rawDomain) ?: return@withContext
        dao.deleteUserDomain(rule.domain)
        domainBlocker.removeDomain(DomainValidator.display(rule))
        loadFromDatabase()
    }

    suspend fun updateBlocklists(urls: List<String> = Constants.DEFAULT_BLOCKLIST_URLS): Int = withContext(Dispatchers.IO) {
        val builtIn = readBuiltInRules()
        val remote = coroutineScope {
            urls.map { url ->
                async(Dispatchers.IO) { parser.downloadAndParseRules(url) }
            }.awaitAll()
        }

        val blocked = LinkedHashSet<String>(Constants.MAX_BLOCKLIST_ENTRIES)
        val allowed = LinkedHashSet<String>(Constants.MAX_ALLOWLIST_ENTRIES)

        builtIn.blocked.forEach { rule -> if (blocked.size < Constants.MAX_BLOCKLIST_ENTRIES) blocked += rule }
        builtIn.allowed.forEach { rule -> if (allowed.size < Constants.MAX_ALLOWLIST_ENTRIES) allowed += rule }
        remote.forEach { parsed ->
            parsed.blocked.forEach { rule ->
                if (blocked.size < Constants.MAX_BLOCKLIST_ENTRIES) blocked += rule
            }
            parsed.allowed.forEach { rule ->
                if (allowed.size < Constants.MAX_ALLOWLIST_ENTRIES) allowed += rule
            }
        }
        if (blocked.isEmpty()) return@withContext 0

        val allowedKeys = allowed.mapNotNull { DomainValidator.normalize(it)?.domain }.toHashSet()
        val finalEntries = LinkedHashMap<String, BlocklistEntry>(blocked.size + allowed.size + 256)

        // User rules always win over downloaded rules.
        dao.getUserEntriesSync().forEach { entry -> finalEntries[entry.domain] = entry }

        allowed.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            finalEntries[normalized.domain] = allowEntry(
                normalized.domain,
                normalized.wildcard,
                SOURCE_ALLOW
            )
        }
        blocked.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            if (normalized.domain in allowedKeys) return@forEach
            finalEntries.putIfAbsent(
                normalized.domain,
                blockEntry(normalized.domain, normalized.wildcard, SOURCE_REMOTE)
            )
        }
        builtIn.dohBypass.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            finalEntries.putIfAbsent(
                normalized.domain,
                blockEntry(normalized.domain, normalized.wildcard, SOURCE_DOH_BYPASS)
            )
        }

        database.withTransaction {
            dao.deleteNonUserEntries()
            chunkedInsert(finalEntries.values.filter { !it.isUserAdded })
        }

        loadFromDatabase()
        preferences.lastBlocklistUpdate = System.currentTimeMillis()
        Logger.i("Updated blocklist with ${finalEntries.size} entries")
        finalEntries.size
    }

    suspend fun getShieldDomains(limit: Int = 800): List<String> = withContext(Dispatchers.IO) {
        val result = LinkedHashSet<String>()
        readRawLines(R.raw.shield_domains).forEach { line ->
            val rule = DomainValidator.normalize(line) ?: return@forEach
            result += rule.domain
            if (result.size >= limit) return@withContext result.toList()
        }
        if (preferences.blockDohBypass) {
            readRawLines(R.raw.doh_bypass_domains).forEach { line ->
                val rule = DomainValidator.normalize(line) ?: return@forEach
                result += rule.domain
                if (result.size >= limit) return@withContext result.toList()
            }
        }
        dao.getUserEntriesSync().forEach { entry ->
            if (entry.source == SOURCE_ALLOW_USER || entry.source == SOURCE_ESSENTIAL) return@forEach
            result += entry.domain
            if (result.size >= limit) return@withContext result.toList()
        }
        result.toList()
    }

    suspend fun reloadRules() = withContext(Dispatchers.IO) {
        loadFromDatabase()
    }

    fun getDefaultBlocklistUrls(): List<String> = Constants.DEFAULT_BLOCKLIST_URLS

    fun getDomainCount(): Int = domainBlocker.domainCount

    private suspend fun chunkedInsert(entries: List<BlocklistEntry>) {
        entries.chunked(900).forEach { chunk -> dao.insertAll(chunk) }
    }

    private fun readBuiltInRules(): BuiltInRules {
        val default = parser.parseRules(readRawText(R.raw.default_blocklist))
        val essential = parser.parseRules(readRawText(R.raw.essential_allowlist))
        val doh = parser.parseRules(readRawText(R.raw.doh_bypass_domains))
        return BuiltInRules(
            blocked = default.blocked,
            allowed = (essential.blocked + essential.allowed).toCollection(LinkedHashSet()),
            dohBypass = doh.blocked
        )
    }

    private fun readRawText(resourceId: Int): String =
        appContext.resources.openRawResource(resourceId).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }

    private fun readRawLines(resourceId: Int): List<String> =
        appContext.resources.openRawResource(resourceId).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readLines() }
        }

    private fun blockEntry(domain: String, wildcard: Boolean, source: String) = BlocklistEntry(
        domain = domain,
        source = source,
        isWildcard = wildcard,
        isUserAdded = false
    )

    private fun allowEntry(domain: String, wildcard: Boolean, source: String) = BlocklistEntry(
        domain = domain,
        source = source,
        isWildcard = wildcard,
        isUserAdded = false
    )

    private data class BuiltInRules(
        val blocked: LinkedHashSet<String>,
        val allowed: LinkedHashSet<String>,
        val dohBypass: LinkedHashSet<String>
    )

    companion object {
        const val SOURCE_DEFAULT = "default"
        const val SOURCE_ESSENTIAL = "essential"
        const val SOURCE_DOH_BYPASS = "doh-bypass"
        const val SOURCE_REMOTE = "remote"
        const val SOURCE_ALLOW = "allow"
        const val SOURCE_USER = "user"
        const val SOURCE_ALLOW_USER = "allow-user"
    }
}
