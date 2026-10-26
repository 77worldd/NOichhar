package com.noichhar.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noichhar.app.NOichharApp
import com.noichhar.app.service.FirewallManager
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.util.Logger
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_REMOVED &&
            action != Intent.ACTION_PACKAGE_REPLACED
        ) return

        val packageName = intent.data?.schemeSpecificPart ?: return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && action == Intent.ACTION_PACKAGE_ADDED) return
        Logger.i("Package changed: $packageName ($action)")

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        NOichharApp.instance.applicationScope.launch {
            try {
                FirewallManager(appContext).refreshAppList()
                NOichharVpnService.instance?.getDnsCache()?.clear()
            } catch (e: Exception) {
                Logger.e("Failed to refresh app list", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
