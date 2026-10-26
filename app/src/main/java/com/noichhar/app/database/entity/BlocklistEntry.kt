package com.noichhar.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocklist")
data class BlocklistEntry(
    @PrimaryKey
    val domain: String,
    val source: String,
    val isWildcard: Boolean = false,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isUserAdded: Boolean = false
)
