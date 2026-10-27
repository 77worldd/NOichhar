package com.noichhar.app.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Anonymous aggregates only: no domain, address or payload is stored here. */
@Entity(tableName = "traffic_stat", indices = [Index("timestamp"), Index("appUid")])
data class TrafficStat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appUid: Int,
    val packageName: String,
    val appName: String,
    val bytesDown: Long,
    val bytesUp: Long,
    val connections: Long,
    val blocked: Long,
    val isMetered: Boolean,
    val timestamp: Long
)
