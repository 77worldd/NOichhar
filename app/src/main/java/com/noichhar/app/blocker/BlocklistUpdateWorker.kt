package com.noichhar.app.blocker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.noichhar.app.NOichharApp
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlocklistUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = NOichharApp.instance
        if (!app.preferenceManager.autoUpdateBlocklists) return@withContext Result.success()

        val lastUpdate = app.preferenceManager.lastBlocklistUpdate
        val fresh = lastUpdate > 0 &&
            System.currentTimeMillis() - lastUpdate < Constants.BLOCKLIST_UPDATE_INTERVAL_MS
        if (fresh && runAttemptCount == 0) return@withContext Result.success()

        return@withContext try {
            val count = app.blocklistManager.updateBlocklists()
            if (count <= 0) {
                Logger.w("Blocklist refresh produced no rules; retrying later")
                if (runAttemptCount < 3) Result.retry() else Result.success()
            } else {
                NOichharVpnService.instance?.refreshProtection()
                Result.success()
            }
        } catch (e: Exception) {
            Logger.e("Automatic blocklist refresh failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
