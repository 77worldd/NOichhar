package com.noichhar.app.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.noichhar.app.database.entity.DnsLog

@Dao
interface DnsLogDao {
    @Insert
    suspend fun insert(log: DnsLog)

    /** One transaction for a whole batch; used by the power-efficient log writer. */
    @Insert
    suspend fun insertAll(logs: List<DnsLog>)

    @Query("SELECT * FROM dns_log ORDER BY timestamp DESC LIMIT 1000")
    fun getRecentLogs(): LiveData<List<DnsLog>>

    @Query("SELECT * FROM dns_log ORDER BY timestamp DESC")
    fun getAllLogs(): LiveData<List<DnsLog>>

    @Query("SELECT * FROM dns_log ORDER BY timestamp DESC")
    suspend fun getAllLogsSync(): List<DnsLog>

    @Query("SELECT * FROM dns_log WHERE domain LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 1000")
    fun searchLogs(query: String): LiveData<List<DnsLog>>

    @Query("SELECT * FROM dns_log WHERE isBlocked = 1 ORDER BY timestamp DESC LIMIT 500")
    fun getBlockedLogs(): LiveData<List<DnsLog>>

    @Query("SELECT COUNT(*) FROM dns_log WHERE isBlocked = 1")
    fun getBlockedCount(): LiveData<Long>

    @Query("SELECT COUNT(*) FROM dns_log WHERE isBlocked = 0")
    fun getAllowedCount(): LiveData<Long>

    @Query("SELECT COUNT(*) FROM dns_log")
    fun getTotalCount(): LiveData<Long>

    @Query("SELECT COUNT(*) FROM dns_log WHERE isBlocked = 1")
    suspend fun getBlockedCountSync(): Long

    @Query("SELECT COUNT(*) FROM dns_log WHERE isBlocked = 0")
    suspend fun getAllowedCountSync(): Long

    @Query("SELECT COUNT(*) FROM dns_log")
    suspend fun getTotalCountSync(): Long

    @Query("DELETE FROM dns_log")
    suspend fun deleteAll()

    @Query("DELETE FROM dns_log WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM dns_log")
    suspend fun getCount(): Int
}
