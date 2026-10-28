package com.noichhar.app.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.noichhar.app.database.entity.ConnectionLog

@Dao
interface ConnectionLogDao {
    @Insert
    suspend fun insert(log: ConnectionLog)

    /** One transaction for a whole batch; used by the power-efficient log writer. */
    @Insert
    suspend fun insertAll(logs: List<ConnectionLog>)

    @Query("SELECT * FROM connection_log ORDER BY timestamp DESC LIMIT 500")
    fun getRecentConnections(): LiveData<List<ConnectionLog>>

    @Query("SELECT * FROM connection_log WHERE appUid = :uid ORDER BY timestamp DESC")
    fun getConnectionsForApp(uid: Int): LiveData<List<ConnectionLog>>

    @Query("SELECT COUNT(*) FROM connection_log")
    suspend fun getCount(): Int

    @Query("DELETE FROM connection_log")
    suspend fun deleteAll()

    @Query("DELETE FROM connection_log WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
