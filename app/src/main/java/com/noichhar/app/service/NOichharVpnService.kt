package com.noichhar.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.blocker.BlocklistManager
import com.noichhar.app.database.entity.DnsLog
import com.noichhar.app.dns.DnsCache
import com.noichhar.app.net.DnsQueryEvent
import com.noichhar.app.net.LocalFilterProxy
import com.noichhar.app.net.NetworkMonitor
import com.noichhar.app.net.NetworkShield
import com.noichhar.app.net.PacketInterceptor
import com.noichhar.app.net.UidResolver
import com.noichhar.app.ui.activity.MainActivity
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import com.noichhar.app.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NOichharVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var packetInterceptor: PacketInterceptor? = null
    private var networkMonitor: NetworkMonitor? = null
    private var localFilterProxy: LocalFilterProxy? = null
    private lateinit var blocklistManager: BlocklistManager
    private lateinit var firewallManager: FirewallManager
    private lateinit var networkShield: NetworkShield
    private val connectionTracker = ConnectionTracker()
    private val dnsCache = DnsCache()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val vpnMutex = Mutex()
    private val stateLock = Any()
    private var lifecycleGeneration = 0L
    @Volatile private var starting = false

    @Volatile
    private var isRunning = false

    @Volatile
    private var foregroundStarted = false

    @Volatile
    private var proxyPort: Int? = null

    @Volatile
    private var activeBlockedIps: Set<String> = emptySet()

    companion object {
        private val counterLock = Any()
        @Volatile var instance: NOichharVpnService? = null
            private set

        @Volatile
        var blockedCount: Long = 0

        @Volatile
        var allowedCount: Long = 0

        @Volatile
        var totalCount: Long = 0

        val isActive: Boolean
            get() = instance?.isRunning == true

        val advancedHostFilteringAvailable: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        blocklistManager = NOichharApp.instance.blocklistManager
        firewallManager = FirewallManager(this)
        networkShield = NetworkShield(applicationContext, NOichharApp.instance.preferenceManager, blocklistManager)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            Constants.ACTION_RELOAD_CONFIG -> {
                if (isRunning) reloadConfiguration() else startVpn()
            }
            Constants.ACTION_REFRESH_APPS -> {
                if (isRunning || starting) {
                    refreshFirewallRules()
                } else {
                    scope.launch {
                        runCatching { firewallManager.refreshAppList() }
                            .onFailure { Logger.e("Failed to refresh firewall rules", it) }
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        val generation = synchronized(stateLock) {
            if (isRunning || starting) return
            startForegroundIfNeeded()
            starting = true
            ++lifecycleGeneration
        }
        scope.launch {
            vpnMutex.withLock {
                try {
                    blocklistManager.initialize()
                    firewallManager.refreshAppList()
                    loadCounters()
                    synchronized(stateLock) {
                        if (generation != lifecycleGeneration || !starting) return@withLock
                        startAdvancedComponents()
                    }

                    // Resolving shield routes can suspend. A stop may arrive while
                    // it runs, so do not publish a stale TUN descriptor afterwards.
                    val descriptor = createVpnInterface()
                    synchronized(stateLock) {
                        if (generation != lifecycleGeneration || !starting) {
                            runCatching { descriptor?.close() }
                            return@withLock
                        }
                        if (descriptor == null) {
                            Logger.e("VPN permission is missing or the interface could not be established")
                            stopVpn()
                            return@withLock
                        }
                        vpnInterface = descriptor
                        startPacketInterceptor(descriptor)
                        startNetworkMonitor()
                        NOichharApp.instance.trafficTracker.start()
                        NOichharApp.instance.preferenceManager.vpnEnabled = true
                        isRunning = true
                        starting = false
                        refreshNotification()
                        broadcastVpnState(true)
                        Logger.i("VPN started with ${activeBlockedIps.size} shielded IP routes")
                    }
                } catch (e: Exception) {
                    synchronized(stateLock) {
                        if (generation == lifecycleGeneration) {
                            Logger.e("Failed to start VPN", e)
                            stopVpn()
                        }
                    }
                }
            }
        }
    }

    private fun startAdvancedComponents() {
        localFilterProxy?.stop()
        localFilterProxy = null
        proxyPort = null

        val preferences = NOichharApp.instance.preferenceManager
        if (!preferences.advancedProtection) {
            activeBlockedIps = emptySet()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val proxy = LocalFilterProxy(
                context = applicationContext,
                domainBlocker = blocklistManager.domainBlocker,
                firewallManager = firewallManager,
                uidResolver = UidResolver(applicationContext),
                protectSocket = { socket -> runCatching { protect(socket) }.getOrDefault(false) },
                onBlocked = { event -> scope.launch { onDnsQuery(event) } }
            )
            val port = proxy.start()
            if (port != null) {
                localFilterProxy = proxy
                proxyPort = port
            }
        }
    }

    private suspend fun createVpnInterface(forceShieldRefresh: Boolean = false): ParcelFileDescriptor? = try {
        val configureIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        activeBlockedIps = networkShield.refreshIfStale(forceShieldRefresh)
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setConfigureIntent(configureIntent)
            .setMtu(Constants.VPN_MTU)
            .addAddress(Constants.VPN_INTERFACE_ADDRESS, 32)
            .addRoute(Constants.VPN_DNS_ADDRESS, 32)
            .addDnsServer(Constants.VPN_DNS_ADDRESS)
            .setBlocking(true)

        activeBlockedIps.forEach { ip ->
            runCatching { builder.addRoute(ip, 32) }
                .onFailure { Logger.w("Skipping invalid network shield route: $ip") }
        }

        val port = proxyPort
        if (port != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val exclusions = listOf("localhost", "127.0.0.1", "*.local")
            runCatching {
                builder.setHttpProxy(
                    ProxyInfo.buildDirectProxy(Constants.LOCAL_PROXY_ADDRESS, port, exclusions)
                )
            }.onFailure { Logger.e("Could not enable hostname-aware app filtering", it) }
        }

        // The resolver and proxy must use the underlying network rather than
        // recursively routing the VPN app's own upstream sockets into its TUN.
        // If this fails, do not establish a broken, self-looping VPN.
        builder.addDisallowedApplication(packageName)
        builder.establish()
    } catch (e: Exception) {
        Logger.e("Failed to establish VPN", e)
        null
    }

    private fun startPacketInterceptor(descriptor: ParcelFileDescriptor) {
        packetInterceptor?.stop()
        packetInterceptor = PacketInterceptor(
            context = applicationContext,
            vpnFileDescriptor = descriptor,
            domainBlocker = blocklistManager.domainBlocker,
            dnsCache = dnsCache,
            preferenceManager = NOichharApp.instance.preferenceManager,
            firewallManager = firewallManager,
            blockedIps = activeBlockedIps,
            networkShield = networkShield,
            onDnsQuery = { event -> scope.launch { onDnsQuery(event) } }
        ).also { it.start() }
    }

    private fun startNetworkMonitor() {
        networkMonitor?.stop()
        networkMonitor = NetworkMonitor(applicationContext).also { monitor ->
            monitor.onNetworkChanged = { available ->
                if (available && isRunning) {
                    Logger.i("Network restored, clearing DNS cache and refreshing shield")
                    dnsCache.clear()
                    scope.launch { runCatching { networkShield.refreshIfStale() } }
                }
            }
            monitor.start()
        }
    }

    private suspend fun loadCounters() {
        val activity = NOichharApp.instance.database.trafficStatDao().activitySince(0L)
        blockedCount = activity.blocked
        allowedCount = activity.allowed
        totalCount = activity.blocked + activity.allowed
    }

    private suspend fun onDnsQuery(event: DnsQueryEvent) {
        NOichharApp.instance.trafficTracker.recordQuery(event)
        synchronized(counterLock) {
            if (event.isBlocked) blockedCount++ else allowedCount++
            totalCount++
        }

        if (totalCount % Constants.LOG_PRUNE_INTERVAL == 0L) refreshNotification()

        if (!NOichharApp.instance.preferenceManager.enableLogs) return

        val database = NOichharApp.instance.database
        runCatching {
            database.dnsLogDao().insert(
                DnsLog(
                    domain = event.domain,
                    queryType = event.queryType,
                    responseIp = event.responseIp,
                    isBlocked = event.isBlocked,
                    blockReason = event.blockReason,
                    responseTimeMs = event.responseTimeMs,
                    timestamp = System.currentTimeMillis(),
                    appUid = event.appUid.takeIf { it >= 0 },
                    appName = event.appName.takeIf { it.isNotBlank() }
                )
            )

            val destinationPort = when (event.queryType) {
                "HTTP" -> Constants.HTTP_PORT
                "HTTPS" -> Constants.HTTPS_PORT
                "IP" -> 0
                else -> Constants.DNS_PORT
            }
            connectionTracker.logConnection(
                appUid = event.appUid,
                appName = event.appName,
                destinationIp = event.domain,
                destinationPort = destinationPort,
                protocol = if (event.isBlocked) "BLOCKED" else "ALLOWED",
                isBlocked = event.isBlocked
            )

            if (totalCount % Constants.LOG_PRUNE_INTERVAL == 0L) {
                val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                database.dnsLogDao().deleteOlderThan(cutoff)
            }
        }.onFailure { Logger.e("Failed to log DNS query", it) }
    }

    fun reloadConfiguration(forceShieldRefresh: Boolean = false) {
        val generation = synchronized(stateLock) { if (isRunning) lifecycleGeneration else null }
        if (generation == null) {
            scope.launch { runCatching { networkShield.refreshIfStale(forceShieldRefresh) } }
            return
        }
        scope.launch {
            vpnMutex.withLock {
                try {
                    synchronized(stateLock) {
                        if (!isRunning || generation != lifecycleGeneration) return@withLock
                        packetInterceptor?.stop()
                        dnsCache.clear()
                        startAdvancedComponents()
                    }
                    val descriptor = createVpnInterface(forceShieldRefresh)
                    synchronized(stateLock) {
                        if (!isRunning || generation != lifecycleGeneration) {
                            runCatching { descriptor?.close() }
                            return@withLock
                        }
                        if (descriptor == null) {
                            stopVpn()
                            return@withLock
                        }
                        runCatching { vpnInterface?.close() }
                        vpnInterface = descriptor
                        startPacketInterceptor(descriptor)
                        Logger.i("VPN configuration reloaded")
                    }
                } catch (e: Exception) {
                    synchronized(stateLock) {
                        if (generation == lifecycleGeneration) {
                            Logger.e("Failed to reload VPN configuration", e)
                            stopVpn()
                        }
                    }
                }
            }
        }
    }

    fun refreshProtection() = reloadConfiguration(forceShieldRefresh = true)

    fun refreshFirewallRules() {
        scope.launch {
            runCatching {
                firewallManager.refreshAppList()
                dnsCache.clear()
            }.onFailure { Logger.e("Failed to refresh firewall rules", it) }
        }
    }

    fun stopVpn() = synchronized(stateLock) {
        lifecycleGeneration++
        starting = false
        isRunning = false
        NOichharApp.instance.preferenceManager.vpnEnabled = false

        packetInterceptor?.stop()
        packetInterceptor = null
        NOichharApp.instance.trafficTracker.stop()
        networkMonitor?.stop()
        networkMonitor = null
        localFilterProxy?.stop()
        localFilterProxy = null
        proxyPort = null
        activeBlockedIps = emptySet()
        dnsCache.clear()

        runCatching { vpnInterface?.close() }
        vpnInterface = null

        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        broadcastVpnState(false)
        stopSelf()
        Logger.i("VPN stopped")
    }

    private fun startForegroundIfNeeded() {
        if (foregroundStarted) return
        startForeground(Constants.NOTIFICATION_ID, NotificationHelper.createVpnNotification(this, blockedCount))
        foregroundStarted = true
    }

    private fun refreshNotification() {
        if (!foregroundStarted) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(Constants.NOTIFICATION_ID, NotificationHelper.createVpnNotification(this, blockedCount))
    }

    private fun broadcastVpnState(active: Boolean) {
        val intent = Intent(Constants.ACTION_VPN_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(Constants.EXTRA_VPN_ACTIVE, active)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        stopVpn()
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    fun getBlocklistManager(): BlocklistManager = blocklistManager
    fun getDnsCache(): DnsCache = dnsCache
}
