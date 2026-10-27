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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class BlocklistManager(
    private val context: Context,
    private val database: NOichharDatabase
) {
    val domainBlocker = EnhancedBlocklistEngine()

    private val appContext = context.applicationContext
    private val dao = database.blocklistDao()
    private val preferences = NOichharApp.instance.preferenceManager
    private val parser = HostsFileParser()
    private val initMutex = Mutex()
    private val updateMutex = Mutex()
    private val changeMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (!initialized) changeMutex.withLock {
                if (dao.getCountSync() == 0) loadDefaultBlocklist()
                loadFromDatabaseUnlocked()
                initialized = true
            }
            Unit
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

    suspend fun loadFromDatabase() = changeMutex.withLock { loadFromDatabaseUnlocked() }

    private suspend fun loadFromDatabaseUnlocked() = withContext(Dispatchers.IO) {
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
                    entry.source == SOURCE_ALLOW_USER,
                user = entry.isUserAdded
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
        changeMutex.withLock {
            withContext(NonCancellable) {
                dao.insert(entry)
                loadFromDatabaseUnlocked()
                true
            }
        }
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
        changeMutex.withLock {
            withContext(NonCancellable) {
                dao.insert(entry)
                loadFromDatabaseUnlocked()
                true
            }
        }
    }

    suspend fun removeDomain(rawDomain: String) = withContext(Dispatchers.IO) {
        val rule = DomainValidator.normalize(rawDomain) ?: return@withContext
        changeMutex.withLock {
            withContext(NonCancellable) {
                dao.deleteUserDomain(rule.domain)
                loadFromDatabaseUnlocked()
            }
        }
    }

    suspend fun updateBlocklists(urls: List<String> = Constants.DEFAULT_BLOCKLIST_URLS): Int =
        updateMutex.withLock { updateBlocklistsInternal(urls) }

    private suspend fun updateBlocklistsInternal(urls: List<String>): Int = withContext(Dispatchers.IO) {
        val builtIn = readBuiltInRules()
        val blocked = LinkedHashSet<String>()
        val allowed = LinkedHashSet<String>()
        builtIn.blocked.forEach { rule -> if (blocked.size < Constants.MAX_BLOCKLIST_ENTRIES) blocked += rule }
        builtIn.allowed.forEach { rule -> if (allowed.size < Constants.MAX_ALLOWLIST_ENTRIES) allowed += rule }

        var downloadedAny = false
        for (url in urls) {
            val remaining = Constants.MAX_BLOCKLIST_ENTRIES - blocked.size
            if (remaining <= 0) break
            val parsed = parser.downloadAndParseRules(url, remaining + Constants.MAX_ALLOWLIST_ENTRIES)
            if (parsed.total > 0) downloadedAny = true
            parsed.blocked.forEach { if (blocked.size < Constants.MAX_BLOCKLIST_ENTRIES) blocked += it }
            parsed.allowed.forEach { if (allowed.size < Constants.MAX_ALLOWLIST_ENTRIES) allowed += it }
        }
        if (!downloadedAny) {
            Logger.w("All blocklist downloads failed or were empty; keeping the existing rules")
            return@withContext 0
        }

        val allowedKeys = allowed.mapNotNull { DomainValidator.normalize(it)?.domain }.toHashSet()
        val downloaded = LinkedHashMap<String, BlocklistEntry>(blocked.size + allowed.size + 256)

        allowed.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            downloaded[normalized.domain] = allowEntry(
                normalized.domain,
                normalized.wildcard,
                SOURCE_ALLOW
            )
        }
        blocked.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            if (normalized.domain in allowedKeys) return@forEach
            downloaded.putIfAbsent(
                normalized.domain,
                blockEntry(normalized.domain, normalized.wildcard, SOURCE_REMOTE)
            )
        }
        builtIn.dohBypass.forEach { rule ->
            val normalized = DomainValidator.normalize(rule) ?: return@forEach
            downloaded.putIfAbsent(
                normalized.domain,
                blockEntry(normalized.domain, normalized.wildcard, SOURCE_DOH_BYPASS)
            )
        }

        // Serialize the transaction and snapshot publication with user edits so
        // a stale snapshot cannot overwrite a newer user rule. Downloads above
        // do not hold this lock, so adding a rule remains responsive.
        changeMutex.withLock {
            withContext(NonCancellable) {
                database.withTransaction {
                    dao.deleteNonUserEntries()
                    chunkedInsert(downloaded.values.toList())
                }
                blocked.clear()
                allowed.clear()
                downloaded.clear()
                loadFromDatabaseUnlocked()
                preferences.lastBlocklistUpdate = System.currentTimeMillis()
                val count = dao.getCountSync()
                Logger.i("Updated blocklist with $count entries")
                count
            }
        }
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
