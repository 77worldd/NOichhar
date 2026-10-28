package com.noichhar.app.stats

import com.noichhar.app.database.NOichharDatabase
import com.noichhar.app.database.entity.ConnectionLog
import com.noichhar.app.database.entity.DnsLog
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/**
 * Batched writer for DNS and connection logs.
 *
 * Previously every resolved query opened two separate Room transactions. Browsing
 * produces many queries per second, so that pattern woke the CPU and touched flash
 * storage constantly — it was the app's largest avoidable battery cost. Rows are now
 * queued in memory and written in a single transaction every couple of seconds, or
 * immediately when the queue fills up. Counters and charts read from memory, so the
 * UI is not delayed by the batching.
 */
class QueryLogWriter(private val database: NOichharDatabase, private val scope: CoroutineScope) {

    private val lock = Any()
    private val dnsQueue = ArrayDeque<DnsLog>()
    private val connectionQueue = ArrayDeque<ConnectionLog>()
    private var flushJob: Job? = null

    private val dnsDao get() = database.dnsLogDao()
    private val connectionDao get() = database.connectionLogDao()

    fun start() {
        if (flushJob?.isActive == true) return
        flushJob = scope.launch {
            while (isActive) {
                delay(Constants.LOG_BATCH_FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    fun recordDns(log: DnsLog) {
        val flushNow = synchronized(lock) {
            if (dnsQueue.size >= Constants.LOG_QUEUE_LIMIT) dnsQueue.pollFirst()
            dnsQueue.addLast(log)
            dnsQueue.size >= Constants.LOG_BATCH_SIZE
        }
        if (flushNow) scope.launch { flush() }
    }

    fun recordConnection(log: ConnectionLog) {
        val flushNow = synchronized(lock) {
            if (connectionQueue.size >= Constants.LOG_QUEUE_LIMIT) connectionQueue.pollFirst()
            connectionQueue.addLast(log)
            connectionQueue.size >= Constants.LOG_BATCH_SIZE
        }
        if (flushNow) scope.launch { flush() }
    }

    /** Writes everything queued so far. Safe to call from any coroutine. */
    suspend fun flush() {
        val batch = synchronized(lock) {
            if (dnsQueue.isEmpty() && connectionQueue.isEmpty()) return
            val dns = dnsQueue.toList()
            val connections = connectionQueue.toList()
            dnsQueue.clear()
            connectionQueue.clear()
            dns to connections
        }
        val (dnsBatch, connectionBatch) = batch

        if (dnsBatch.isNotEmpty()) {
            runCatching { dnsDao.insertAll(dnsBatch) }.onFailure {
                Logger.e("Failed to write DNS log batch", it)
                synchronized(lock) { dnsBatch.asReversed().forEach { log -> dnsQueue.addFirst(log) } }
            }
        }
        if (connectionBatch.isNotEmpty()) {
            runCatching { connectionDao.insertAll(connectionBatch) }.onFailure {
                Logger.e("Failed to write connection log batch", it)
                synchronized(lock) {
                    while (connectionQueue.size > Constants.LOG_QUEUE_LIMIT) connectionQueue.pollFirst()
                    connectionBatch.asReversed().forEach { log -> connectionQueue.addFirst(log) }
                }
            }
        }
    }

    /** Trims old rows; called on a slow timer instead of every few dozen queries. */
    suspend fun prune() {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        runCatching {
            if (dnsDao.getCount() > Constants.MAX_LOG_ENTRIES) dnsDao.deleteOlderThan(cutoff)
            if (connectionDao.getCount() > Constants.MAX_LOG_ENTRIES) connectionDao.deleteOlderThan(cutoff)
        }.onFailure { Logger.e("Failed to prune stored logs", it) }
    }

    fun stop() {
        flushJob?.cancel()
        flushJob = null
        // The trailing batch still matters for the last few queries; write it on the
        // application scope, which outlives the service.
        scope.launch { flush() }
    }
}
