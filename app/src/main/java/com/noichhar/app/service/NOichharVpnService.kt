package com.noichhar.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.blocker.BlocklistManager
import com.noichhar.app.database.entity.DnsLog
import com.noichhar.app.dns.DnsCache
import com.noichhar.app.net.DnsQueryEvent
import com.noichhar.app.net.NetworkMonitor
import com.noichhar.app.net.PacketInterceptor
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
    private lateinit var blocklistManager: BlocklistManager
    private lateinit var firewallManager: FirewallManager
    private val connectionTracker = ConnectionTracker()
    private val dnsCache = DnsCache()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val vpnMutex = Mutex()

    @Volatile
    private var isRunning = false

    @Volatile
    private var foregroundStarted = false

    companion object {
        var instance: NOichharVpnService? = null
            private set

        @Volatile
        var blockedCount: Long = 0

        @Volatile
        var allowedCount: Long = 0

        @Volatile
        var totalCount: Long = 0

        val isActive: Boolean
            get() = instance?.isRunning == true
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        blocklistManager = NOichharApp.instance.blocklistManager
        firewallManager = FirewallManager(this)
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
                if (isRunning) {
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
        if (isRunning) return
        startForegroundIfNeeded()
        scope.launch {
            vpnMutex.withLock {
                if (isRunning) return@withLock
                try {
                    blocklistManager.initialize()
                    firewallManager.refreshAppList()
                    loadCounters()

                    val descriptor = createVpnInterface()
                    if (descriptor == null) {
                        Logger.e("VPN permission is missing or the interface could not be established")
                        stopVpn()
                        return@withLock
                    }

                    vpnInterface = descriptor
                    isRunning = true
                    NOichharApp.instance.preferenceManager.vpnEnabled = true
                    startPacketInterceptor(descriptor)
                    startNetworkMonitor()
                    refreshNotification()
                    broadcastVpnState(true)
                    Logger.i("VPN started successfully")
                } catch (e: Exception) {
                    Logger.e("Failed to start VPN", e)
                    stopVpn()
                }
            }
        }
    }

    private fun createVpnInterface(): ParcelFileDescriptor? = try {
        val configureIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Builder()
            .setSession(getString(R.string.app_name))
            .setConfigureIntent(configureIntent)
            .setMtu(Constants.VPN_MTU)
            .addAddress(Constants.VPN_INTERFACE_ADDRESS, 32)
            .addRoute(Constants.VPN_DNS_ADDRESS, 32)
            .addDnsServer(Constants.VPN_DNS_ADDRESS)
            .setBlocking(true)
            .also { builder ->
                runCatching { builder.addDisallowedApplication(packageName) }
            }
            .establish()
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
            onDnsQuery = { event -> scope.launch { onDnsQuery(event) } }
        ).also { it.start() }
    }

    private fun startNetworkMonitor() {
        networkMonitor?.stop()
        networkMonitor = NetworkMonitor(applicationContext).also { monitor ->
            monitor.onNetworkChanged = { available ->
                if (available && isRunning) {
                    Logger.i("Network restored, clearing DNS cache")
                    dnsCache.clear()
                }
            }
            monitor.start()
        }
    }

    private suspend fun loadCounters() {
        val dao = NOichharApp.instance.database.dnsLogDao()
        blockedCount = dao.getBlockedCountSync()
        allowedCount = dao.getAllowedCountSync()
        totalCount = dao.getTotalCountSync()
    }

    private suspend fun onDnsQuery(event: DnsQueryEvent) {
        if (event.isBlocked) blockedCount++ else allowedCount++
        totalCount++

        if (totalCount % 50 == 0L) refreshNotification()

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

            connectionTracker.logConnection(
                appUid = event.appUid,
                appName = event.appName,
                destinationIp = event.domain,
                destinationPort = Constants.DNS_PORT,
                protocol = if (event.isBlocked) "BLOCKED" else "ALLOWED",
                isBlocked = event.isBlocked
            )

            if (database.dnsLogDao().getCount() > Constants.MAX_LOG_ENTRIES) {
                val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
                database.dnsLogDao().deleteOlderThan(cutoff)
            }
        }.onFailure { Logger.e("Failed to log DNS query", it) }
    }

    fun reloadConfiguration() {
        if (!isRunning) return
        scope.launch {
            vpnMutex.withLock {
                if (!isRunning) return@withLock
                packetInterceptor?.stop()
                dnsCache.clear()
                val descriptor = createVpnInterface()
                if (descriptor == null) {
                    stopVpn()
                    return@withLock
                }
                runCatching { vpnInterface?.close() }
                vpnInterface = descriptor
                startPacketInterceptor(descriptor)
                Logger.i("VPN configuration reloaded")
            }
        }
    }

    fun refreshFirewallRules() {
        scope.launch {
            runCatching {
                firewallManager.refreshAppList()
                dnsCache.clear()
            }.onFailure { Logger.e("Failed to refresh firewall rules", it) }
        }
    }

    fun stopVpn() {
        isRunning = false
        NOichharApp.instance.preferenceManager.vpnEnabled = false

        packetInterceptor?.stop()
        packetInterceptor = null
        networkMonitor?.stop()
        networkMonitor = null
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
