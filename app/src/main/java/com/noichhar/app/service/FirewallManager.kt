package com.noichhar.app.service

import android.content.Context
import com.noichhar.app.NOichharApp
import com.noichhar.app.database.entity.AppInfo
import com.noichhar.app.util.NetworkUtils
import com.noichhar.app.util.PackageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FirewallDecision(val allowed: Boolean, val reason: String? = null)

class FirewallManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val database = NOichharApp.instance.database
    private val appInfoDao = database.appInfoDao()
    private val preferences = NOichharApp.instance.preferenceManager

    suspend fun refreshAppList() = withContext(Dispatchers.IO) {
        val installedApps = PackageUtils.getInstalledApps(appContext)
        val existingApps = appInfoDao.getAllAppsList().associateBy { it.uid }
        val defaultAllowed = !preferences.blockNewApps

        val appsToInsert = installedApps.map { app ->
            val existing = existingApps[app.uid]
            if (existing != null) {
                existing.copy(
                    packageName = app.packageName,
                    appName = app.appName,
                    isSystemApp = app.isSystemApp
                )
            } else {
                app.copy(
                    isInternetAllowed = defaultAllowed || app.packageName == appContext.packageName,
                    wifiAllowed = defaultAllowed,
                    mobileAllowed = defaultAllowed,
                    backgroundAllowed = defaultAllowed
                )
            }
        }

        appInfoDao.deleteAll()
        appInfoDao.insertAll(appsToInsert)
    }

    suspend fun evaluateDns(uid: Int, screenInteractive: Boolean): FirewallDecision = withContext(Dispatchers.IO) {
        val app = if (uid >= 0) appInfoDao.getAppByUid(uid) else null
        if (app?.packageName == appContext.packageName) {
            return@withContext FirewallDecision(true)
        }

        if (preferences.lockdownMode && !screenInteractive) {
            if (app == null || !app.backgroundAllowed) {
                return@withContext FirewallDecision(false, "Background DNS blocked while screen is locked")
            }
        }

        if (app == null) {
            return@withContext if (preferences.blockUnknown) {
                FirewallDecision(false, "Unknown application")
            } else {
                FirewallDecision(true)
            }
        }

        if (!app.isInternetAllowed) {
            return@withContext FirewallDecision(false, "Blocked by app rule")
        }
        if (NetworkUtils.isWifi(appContext) && !app.wifiAllowed) {
            return@withContext FirewallDecision(false, "Blocked on Wi-Fi")
        }
        if (NetworkUtils.isMobile(appContext) && !app.mobileAllowed) {
            return@withContext FirewallDecision(false, "Blocked on mobile data")
        }
        FirewallDecision(true)
    }

    suspend fun setAppBlocked(uid: Int, blocked: Boolean) = withContext(Dispatchers.IO) {
        appInfoDao.setInternetAllowed(uid, !blocked)
    }

    suspend fun setWifiAllowed(uid: Int, allowed: Boolean) = withContext(Dispatchers.IO) {
        appInfoDao.setWifiAllowed(uid, allowed)
    }

    suspend fun setMobileAllowed(uid: Int, allowed: Boolean) = withContext(Dispatchers.IO) {
        appInfoDao.setMobileAllowed(uid, allowed)
    }

    suspend fun setBackgroundAllowed(uid: Int, allowed: Boolean) = withContext(Dispatchers.IO) {
        appInfoDao.setBackgroundAllowed(uid, allowed)
    }

    suspend fun isAppAllowed(uid: Int): Boolean = withContext(Dispatchers.IO) {
        appInfoDao.getAppByUid(uid)?.isInternetAllowed ?: !preferences.blockUnknown
    }

    suspend fun getBlockedApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        appInfoDao.getBlockedApps()
    }
}
