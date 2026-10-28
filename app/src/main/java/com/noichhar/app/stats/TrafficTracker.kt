package com.noichhar.app.stats

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.noichhar.app.NOichharApp
import com.noichhar.app.database.entity.TrafficStat
import com.noichhar.app.net.DnsQueryEvent
import com.noichhar.app.util.Logger
import com.noichhar.app.util.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Device-wide rates come from TrafficStats. Per-UID bytes come from Android's
 * NetworkStatsManager and need Usage Access. Android may report coarse, delayed
 * buckets and may deny mobile data; this is not a packet or connection counter.
 * Filtered-request counts are recorded separately from real VPN events.
 */
class TrafficTracker(private val context: Context) {
    data class Rates(val down: Long? = null, val up: Long? = null)
    data class Coverage(val usageAccess: Boolean = false, val wifiAvailable: Boolean = false,
                        val mobileAvailable: Boolean = false)
    private data class Key(val uid: Int, val metered: Boolean)
    private data class Bytes(val down: Long, val up: Long)
    private data class Events(val uid: Int, val name: String, val packageName: String,
                              var count: Long = 0, var blocked: Long = 0)

    private val app = context.applicationContext as NOichharApp
    private val dao get() = app.database.trafficStatDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flushMutex = Mutex()
    private val pending = mutableMapOf<Int, Events>()
    private val baselines = mutableMapOf<Int, Map<Key, Bytes>>()
    private var job: Job? = null
    private val _rates = MutableStateFlow(Rates())
    val rates = _rates.asStateFlow()
    private val _coverage = MutableStateFlow(Coverage())
    val coverage = _coverage.asStateFlow()

    /**
     * Number of screens currently showing traffic data. When nobody is looking, the
     * tracker stops sampling byte rates and drops per-app snapshots to a slow timer:
     * a VPN left on overnight should cost close to nothing beyond DNS interception.
     */
    private val uiConsumers = AtomicInteger(0)

    @Volatile
    private var snapshotNow = false

    fun onUiVisible() {
        // Ask for a fresh sample on the next tick so a reopened screen is never stale.
        if (uiConsumers.incrementAndGet() == 1) snapshotNow = true
    }

    fun onUiHidden() {
        if (uiConsumers.decrementAndGet() < 0) uiConsumers.set(0)
    }

    fun start() {
        if (job?.isActive == true) return
        baselines.clear()
        val startedAt = System.currentTimeMillis()
        job = scope.launch {
            var lastDown = TrafficStats.getTotalRxBytes()
            var lastUp = TrafficStats.getTotalTxBytes()
            var lastTick = SystemClock.elapsedRealtime()
            var lastSnapshot = 0L
            var lastPrune = 0L
            while (isActive) {
                val uiVisible = uiConsumers.get() > 0
                val tick = SystemClock.elapsedRealtime()
                if (uiVisible) {
                    val down = TrafficStats.getTotalRxBytes()
                    val up = TrafficStats.getTotalTxBytes()
                    val elapsed = (tick - lastTick).coerceAtLeast(1L)
                    val downRate = if (down >= 0 && lastDown >= 0 && down >= lastDown)
                        (down - lastDown) * 1000L / elapsed else null
                    val upRate = if (up >= 0 && lastUp >= 0 && up >= lastUp)
                        (up - lastUp) * 1000L / elapsed else null
                    _rates.value = Rates(downRate, upRate)
                    lastDown = down; lastUp = up
                } else if (_rates.value != Rates()) {
                    // Nothing is displaying the waveform; publish an empty sample once
                    // and stop reading counters until a screen returns.
                    _rates.value = Rates()
                    lastDown = TrafficStats.getTotalRxBytes()
                    lastUp = TrafficStats.getTotalTxBytes()
                }
                lastTick = tick

                runCatching { flushEvents() }.onFailure { Logger.e("Could not save request statistics", it) }

                val snapshotInterval = if (uiVisible) SNAPSHOT_INTERVAL_UI_MS else SNAPSHOT_INTERVAL_IDLE_MS
                if (snapshotNow || lastSnapshot == 0L || tick - lastSnapshot >= snapshotInterval) {
                    snapshotNow = false
                    runCatching { snapshotPerApp(startedAt) }
                        .onFailure { Logger.e("Could not read Android network statistics", it) }
                    lastSnapshot = tick
                }
                if (lastPrune == 0L || tick - lastPrune >= 3_600_000L) {
                    runCatching { dao.deleteOlderThan(System.currentTimeMillis() - RETENTION_MS) }
                    lastPrune = tick
                }
                delay(if (uiVisible) TICK_UI_MS else TICK_IDLE_MS)
            }
        }
    }

    fun stop() {
        val oldJob = job
        job = null
        oldJob?.cancel()
        _rates.value = Rates()
        // The service can be destroyed immediately; use the application scope to
        // await the old job and persist its final batch without blocking the UI.
        app.applicationScope.launch {
            oldJob?.cancelAndJoin()
            runCatching { flushEvents() }.onFailure { Logger.e("Could not save final request statistics", it) }
        }
    }

    fun recordQuery(event: DnsQueryEvent) {
        val uid = event.appUid
        val pkg = if (uid >= 0) PackageUtils.getPackageNameForUid(context, uid).orEmpty() else ""
        synchronized(pending) {
            val value = pending.getOrPut(uid) { Events(uid, event.appName.ifBlank { "Unknown" }, pkg) }
            value.count++
            if (event.isBlocked) value.blocked++
        }
    }

    private suspend fun flushEvents() = flushMutex.withLock {
        val batch = synchronized(pending) {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        if (batch.isEmpty()) return@withLock
        val now = System.currentTimeMillis()
        try {
            dao.insertAll(batch.map { TrafficStat(appUid = it.uid, packageName = it.packageName,
                appName = it.name, bytesDown = 0, bytesUp = 0, connections = it.count,
                blocked = it.blocked, isMetered = false, timestamp = now) })
        } catch (e: Exception) {
            synchronized(pending) {
                batch.forEach { old ->
                    val value = pending.getOrPut(old.uid) { Events(old.uid, old.name, old.packageName) }
                    value.count += old.count; value.blocked += old.blocked
                }
            }
            throw e
        }
    }

    fun hasUsageStatsPermission(): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    private suspend fun snapshotPerApp(startedAt: Long) {
        if (!hasUsageStatsPermission()) {
            baselines.clear()
            _coverage.value = Coverage()
            return
        }
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
        val now = System.currentTimeMillis()
        var wifiAvailable = false
        var mobileAvailable = false
        val rows = mutableListOf<TrafficStat>()
        for (network in listOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            val current = try { readNetwork(manager, network, startedAt, now) }
            catch (e: Exception) {
                Logger.w("Network usage unavailable for type $network: ${e.message}")
                baselines.remove(network)
                continue
            }
            if (network == ConnectivityManager.TYPE_WIFI) wifiAvailable = true else mobileAvailable = true
            val previous = baselines.put(network, current) ?: continue // establish baseline, do not invent history
            for ((key, value) in current) {
                val old = previous[key] ?: Bytes(0, 0)
                val down = (value.down - old.down).coerceAtLeast(0)
                val up = (value.up - old.up).coerceAtLeast(0)
                if (down == 0L && up == 0L) continue
                val pkg = if (key.uid >= 0) PackageUtils.getPackageNameForUid(context, key.uid).orEmpty() else ""
                val name = if (key.uid >= 0) PackageUtils.getAppName(context, key.uid) else "System / unassigned"
                rows += TrafficStat(appUid = key.uid, packageName = pkg, appName = name,
                    bytesDown = down, bytesUp = up, connections = 0, blocked = 0,
                    isMetered = key.metered, timestamp = now)
            }
        }
        if (rows.isNotEmpty()) dao.insertAll(rows)
        _coverage.value = Coverage(true, wifiAvailable, mobileAvailable)
    }

    @Suppress("DEPRECATION")
    private fun readNetwork(manager: NetworkStatsManager, type: Int, start: Long, end: Long): Map<Key, Bytes> {
        val result = mutableMapOf<Key, Bytes>()
        manager.querySummary(type, null, start, end).use { summary ->
            val bucket = NetworkStats.Bucket()
            while (summary.hasNextBucket()) {
                summary.getNextBucket(bucket)
                // Metered classification exists on Android 8+. Older Android
                // releases expose only transport; Wi-Fi/mobile is an estimate.
                val metered = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    when (bucket.metered) { 1 -> false; 2 -> true; else -> type == ConnectivityManager.TYPE_MOBILE }
                } else type == ConnectivityManager.TYPE_MOBILE
                val key = Key(bucket.uid, metered)
                val old = result[key] ?: Bytes(0, 0)
                result[key] = Bytes(old.down + bucket.rxBytes, old.up + bucket.txBytes)
            }
        }
        return result
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 3_600_000L
        private const val TICK_UI_MS = 2_000L
        private const val TICK_IDLE_MS = 30_000L
        private const val SNAPSHOT_INTERVAL_UI_MS = 30_000L
        private const val SNAPSHOT_INTERVAL_IDLE_MS = 300_000L
    }
}
