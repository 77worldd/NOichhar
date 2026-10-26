package com.noichhar.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.noichhar.app.blocker.BlocklistManager
import com.noichhar.app.database.NOichharDatabase
import com.noichhar.app.service.FirewallManager
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import com.noichhar.app.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

        applicationScope.launch {
            runCatching {
                blocklistManager.initialize()
                FirewallManager(this@NOichharApp).refreshAppList()
            }.onFailure { Logger.e("Failed to initialize local data", it) }
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

    override fun onTerminate() {
        super.onTerminate()
    }

    companion object {
        lateinit var instance: NOichharApp
            private set
    }
}
