package com.noichhar.app.blocker

import android.content.Context
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.database.NOichharDatabase
import com.noichhar.app.database.entity.BlocklistEntry
import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import kotlinx.coroutines.Dispatchers
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

    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (initialized) return@withLock
            if (database.blocklistDao().getCountSync() == 0) loadDefaultBlocklist()
            loadFromDatabase()
            initialized = true
        }
    }

    private suspend fun loadDefaultBlocklist() {
        val entries = mutableListOf<BlocklistEntry>()
        runCatching {
            context.resources.openRawResource(R.raw.default_blocklist).use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        val rule = DomainValidator.normalize(line) ?: return@forEach
                        entries += BlocklistEntry(
                            domain = rule.domain,
                            source = "default",
                            isWildcard = rule.wildcard,
                            isUserAdded = false
                        )
                    }
                }
            }
            database.blocklistDao().insertAll(entries.distinctBy { it.domain })
            Logger.i("Loaded ${entries.size} default blocklist entries")
        }.onFailure { Logger.e("Failed to load default blocklist", it) }
    }

    suspend fun loadFromDatabase() = withContext(Dispatchers.IO) {
        val entries = database.blocklistDao().getAllSync()
        val domains = entries.map { entry ->
            if (entry.isWildcard) "*.${entry.domain}" else entry.domain
        }
        domainBlocker.loadDomains(domains)
    }

    suspend fun addCustomDomain(rawDomain: String): Boolean = withContext(Dispatchers.IO) {
        val rule = DomainValidator.normalize(rawDomain) ?: return@withContext false
        val entry = BlocklistEntry(
            domain = rule.domain,
            source = "user",
            isWildcard = rule.wildcard,
            isUserAdded = true
        )
        database.blocklistDao().insert(entry)
        domainBlocker.addDomain(DomainValidator.display(rule))
        true
    }

    suspend fun removeDomain(rawDomain: String) = withContext(Dispatchers.IO) {
        val rule = DomainValidator.normalize(rawDomain) ?: return@withContext
        database.blocklistDao().deleteUserDomain(rule.domain)
        domainBlocker.removeDomain(DomainValidator.display(rule))
    }

    suspend fun updateBlocklists(urls: List<String> = getDefaultBlocklistUrls()): Int = withContext(Dispatchers.IO) {
        val parser = HostsFileParser()
        val domains = LinkedHashSet<String>()

        for (url in urls) {
            runCatching { domains.addAll(parser.downloadAndParse(url)) }
                .onFailure { Logger.e("Failed to download blocklist: $url", it) }
        }

        if (domains.isEmpty()) return@withContext 0

        val entries = domains.mapNotNull { raw ->
            DomainValidator.normalize(raw)?.let { rule ->
                BlocklistEntry(
                    domain = rule.domain,
                    source = "remote",
                    isWildcard = rule.wildcard,
                    isUserAdded = false
                )
            }
        }.distinctBy { it.domain }

        database.blocklistDao().deleteNonUserEntries()
        database.blocklistDao().insertAll(entries)
        loadFromDatabase()
        NOichharApp.instance.preferenceManager.lastBlocklistUpdate = System.currentTimeMillis()
        entries.size
    }

    fun getDefaultBlocklistUrls(): List<String> = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
    )

    fun getDomainCount(): Int = domainBlocker.domainCount
}
