package com.noichhar.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.noichhar.app.blocker.BlocklistManager
import com.noichhar.app.blocker.BlocklistUpdateWorker
import com.noichhar.app.database.NOichharDatabase
import com.noichhar.app.service.FirewallManager
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import com.noichhar.app.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NOichharApp : Application() {

    lateinit var database: NOichharDatabase
        private set
    lateinit var preferenceManager: PreferenceManager
        private set
    lateinit var blocklistManager: BlocklistManager
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = NOichharDatabase.getInstance(this)
        preferenceManager = PreferenceManager(this)
        AppCompatDelegate.setDefaultNightMode(
            if (preferenceManager.darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        blocklistManager = BlocklistManager(this, database)
        createNotificationChannel()
        scheduleBlocklistUpdates()

        applicationScope.launch {
            runCatching {
                blocklistManager.initialize()
                FirewallManager(this@NOichharApp).refreshAppList()
            }.onFailure { Logger.e("Failed to initialize local data", it) }
        }
    }

    fun scheduleBlocklistUpdates() {
        val workManager = WorkManager.getInstance(this)
        if (!preferenceManager.autoUpdateBlocklists) {
            workManager.cancelUniqueWork(Constants.WORK_BLOCKLIST_PERIODIC)
            workManager.cancelUniqueWork(Constants.WORK_BLOCKLIST_IMMEDIATE)
            return
        }

        val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val periodic = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(12, TimeUnit.HOURS)
            .setConstraints(periodicConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            Constants.WORK_BLOCKLIST_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            periodic
        )

        if (preferenceManager.lastBlocklistUpdate == 0L) {
            val immediateConstraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val immediate = OneTimeWorkRequestBuilder<BlocklistUpdateWorker>()
                .setConstraints(immediateConstraints)
                .build()
            workManager.enqueueUniqueWork(
                Constants.WORK_BLOCKLIST_IMMEDIATE,
                ExistingWorkPolicy.KEEP,
                immediate
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        lateinit var instance: NOichharApp
            private set
    }
}
