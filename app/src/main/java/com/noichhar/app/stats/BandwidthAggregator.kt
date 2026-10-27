package com.noichhar.app.stats

import com.noichhar.app.NOichharApp
import com.noichhar.app.database.dao.AppTrafficSummary
import com.noichhar.app.database.dao.TrafficTotals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BandwidthAggregator {
    private val dao = NOichharApp.instance.database.trafficStatDao()
    suspend fun totalsFor(window: TimeWindow): TrafficTotals = withContext(Dispatchers.IO) {
        dao.totalsSince(window.sinceMillis())
    }
    suspend fun topConnections(window: TimeWindow, limit: Int = 10): List<AppTrafficSummary> =
        withContext(Dispatchers.IO) { dao.topConnections(window.sinceMillis(), limit) }
    suspend fun topTrafficApps(window: TimeWindow, limit: Int = 10): List<AppTrafficSummary> =
        withContext(Dispatchers.IO) { dao.topTrafficApps(window.sinceMillis(), limit) }
}
