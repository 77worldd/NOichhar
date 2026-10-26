package com.noichhar.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_info")
data class AppInfo(
    @PrimaryKey
    val uid: Int,
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    var isInternetAllowed: Boolean = true,
    var wifiAllowed: Boolean = true,
    var mobileAllowed: Boolean = true,
    var backgroundAllowed: Boolean = true
)
