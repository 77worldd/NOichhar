package com.noichhar.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_log")
data class DnsLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val domain: String,
    val queryType: String,
    val responseIp: String?,
    val isBlocked: Boolean,
    val blockReason: String?,
    val responseTimeMs: Long,
    val timestamp: Long,
    val appUid: Int?,
    val appName: String?
)
