package com.noichhar.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.noichhar.app.NOichharApp
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val app = context.applicationContext as? NOichharApp ?: return
        if (!app.preferenceManager.startOnBoot || !app.preferenceManager.vpnEnabled) return
        if (VpnService.prepare(context) != null) {
            app.preferenceManager.vpnEnabled = false
            Logger.w("VPN permission is no longer granted; auto-start skipped")
            return
        }

        val serviceIntent = Intent(context, NOichharVpnService::class.java).apply {
            action = Constants.ACTION_START
        }
        runCatching { ContextCompat.startForegroundService(context, serviceIntent) }
            .onFailure { Logger.e("Failed to start service on boot", it) }
    }
}
