package com.noichhar.app.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.noichhar.app.database.entity.TrafficStat

@Dao
interface TrafficStatDao {
    @Insert suspend fun insertAll(stats: List<TrafficStat>)

    @Query("""SELECT COALESCE(SUM(CASE WHEN isMetered = 0 THEN bytesDown + bytesUp ELSE 0 END), 0) AS unmetered,
        COALESCE(SUM(CASE WHEN isMetered = 1 THEN bytesDown + bytesUp ELSE 0 END), 0) AS metered,
        COALESCE(SUM(bytesDown + bytesUp), 0) AS overall FROM traffic_stat WHERE timestamp >= :since""")
    suspend fun totalsSince(since: Long): TrafficTotals

    @Query("""SELECT COALESCE(SUM(connections - blocked), 0) AS allowed,
        COALESCE(SUM(blocked), 0) AS blocked FROM traffic_stat WHERE timestamp >= :since""")
    fun observeActivity(since: Long): LiveData<ActivityCounts>

    @Query("""SELECT COALESCE(SUM(connections - blocked), 0) AS allowed,
        COALESCE(SUM(blocked), 0) AS blocked FROM traffic_stat WHERE timestamp >= :since""")
    suspend fun activitySince(since: Long): ActivityCounts

    @Query("""SELECT CAST((timestamp - :since) / :bucketMs AS INTEGER) AS bucket,
        SUM(connections) AS count FROM traffic_stat
        WHERE timestamp >= :since AND timestamp < :until AND connections > 0
        GROUP BY CAST((timestamp - :since) / :bucketMs AS INTEGER)""")
    suspend fun activityBuckets(since: Long, until: Long, bucketMs: Long): List<ActivityBucket>

    @Query("""SELECT appUid, MAX(packageName) AS packageName, MAX(appName) AS appName,
        SUM(bytesDown) AS totalDown, SUM(bytesUp) AS totalUp, SUM(connections) AS totalConn
        FROM traffic_stat WHERE timestamp >= :since GROUP BY appUid
        HAVING SUM(connections) > 0 ORDER BY totalConn DESC LIMIT :limit""")
    suspend fun topConnections(since: Long, limit: Int = 20): List<AppTrafficSummary>

    @Query("""SELECT appUid, MAX(packageName) AS packageName, MAX(appName) AS appName,
        SUM(bytesDown) AS totalDown, SUM(bytesUp) AS totalUp, SUM(connections) AS totalConn
        FROM traffic_stat WHERE timestamp >= :since GROUP BY appUid
        HAVING SUM(bytesDown + bytesUp) > 0 ORDER BY (totalDown + totalUp) DESC LIMIT :limit""")
    suspend fun topTrafficApps(since: Long, limit: Int = 20): List<AppTrafficSummary>

    @Query("DELETE FROM traffic_stat WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}

data class TrafficTotals(val unmetered: Long, val metered: Long, val overall: Long)
data class ActivityCounts(val allowed: Long, val blocked: Long)
data class ActivityBucket(val bucket: Int, val count: Long)
data class AppTrafficSummary(
    val appUid: Int, val packageName: String, val appName: String,
    val totalDown: Long, val totalUp: Long, val totalConn: Long
) {
    val totalBytes: Long get() = totalDown + totalUp
}
