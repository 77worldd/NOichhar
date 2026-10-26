package com.noichhar.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_log")
data class ConnectionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appUid: Int,
    val appName: String,
    val destinationIp: String,
    val destinationPort: Int,
    val protocol: String, // TCP or UDP
    val isBlocked: Boolean,
    val timestamp: Long
)
