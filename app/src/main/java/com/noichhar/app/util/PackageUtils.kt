package com.noichhar.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import com.noichhar.app.database.entity.AppInfo
import java.util.concurrent.ConcurrentHashMap

object PackageUtils {

    private val appNameCache = ConcurrentHashMap<Int, String>()
    private val packageNameCache = ConcurrentHashMap<Int, String>()

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }

        return packages.mapNotNull { appInfo ->
            try {
                val uid = appInfo.uid
                val packageName = appInfo.packageName
                val appName = pm.getApplicationLabel(appInfo).toString()
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                AppInfo(
                    uid = uid,
                    packageName = packageName,
                    appName = appName,
                    isSystemApp = isSystemApp,
                    isInternetAllowed = true,
                    wifiAllowed = true,
                    mobileAllowed = true,
                    backgroundAllowed = true
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase() }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
    }

    fun getAppName(context: Context, uid: Int): String {
        appNameCache[uid]?.let { return it }
        val pm = context.packageManager
        val packages = pm.getPackagesForUid(uid)
        if (packages.isNullOrEmpty()) return "Unknown (UID: $uid)"
        val name = try {
            val appInfo = pm.getApplicationInfo(packages[0], 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packages[0]
        }
        appNameCache[uid] = name
        return name
    }

    fun getPackageNameForUid(context: Context, uid: Int): String? {
        packageNameCache[uid]?.let { return it }
        val packageName = context.packageManager.getPackagesForUid(uid)?.firstOrNull()
        if (packageName != null) packageNameCache[uid] = packageName
        return packageName
    }

    fun clearAppInfoCache() {
        appNameCache.clear()
        packageNameCache.clear()
    }
}
