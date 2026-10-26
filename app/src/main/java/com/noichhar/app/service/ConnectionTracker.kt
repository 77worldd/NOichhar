package com.noichhar.app.service

import com.noichhar.app.NOichharApp
import com.noichhar.app.database.entity.ConnectionLog
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConnectionTracker {

    private val database = NOichharApp.instance.database

    suspend fun logConnection(
        appUid: Int,
        appName: String,
        destinationIp: String,
        destinationPort: Int,
        protocol: String,
        isBlocked: Boolean
    ) = withContext(Dispatchers.IO) {
        runCatching {
            database.connectionLogDao().insert(
                ConnectionLog(
                    appUid = appUid,
                    appName = appName,
                    destinationIp = destinationIp,
                    destinationPort = destinationPort,
                    protocol = protocol,
                    isBlocked = isBlocked,
                    timestamp = System.currentTimeMillis()
                )
            )
            trimOldLogs()
        }.onFailure { Logger.e("Failed to log connection", it) }
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        database.connectionLogDao().deleteAll()
    }

    private suspend fun trimOldLogs() {
        if (database.connectionLogDao().getCount() > Constants.MAX_LOG_ENTRIES) {
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            database.connectionLogDao().deleteOlderThan(cutoff)
        }
    }
}
