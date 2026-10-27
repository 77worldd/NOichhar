package com.noichhar.app.stats

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noichhar.app.NOichharApp
import com.noichhar.app.util.Logger

/** Retention also runs while the VPN is off (subject to Android's scheduler). */
class StatsPruneWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as NOichharApp
        app.database.trafficStatDao().deleteOlderThan(System.currentTimeMillis() - TrafficTracker.RETENTION_MS)
        Result.success()
    } catch (e: Exception) {
        Logger.e("Could not prune old traffic statistics", e)
        Result.retry()
    }
}
