package com.noichhar.app.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.noichhar.app.database.entity.AppInfo

@Dao
interface AppInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppInfo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: AppInfo)

    @Update
    suspend fun update(app: AppInfo)

    @Delete
    suspend fun delete(app: AppInfo)

    @Query("SELECT * FROM app_info ORDER BY appName ASC")
    fun getAllApps(): LiveData<List<AppInfo>>

    @Query("SELECT * FROM app_info ORDER BY appName ASC")
    suspend fun getAllAppsList(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE uid = :uid")
    suspend fun getAppByUid(uid: Int): AppInfo?

    @Query("SELECT * FROM app_info WHERE packageName = :packageName")
    suspend fun getAppByPackageName(packageName: String): AppInfo?

    @Query("SELECT * FROM app_info WHERE isInternetAllowed = 0")
    suspend fun getBlockedApps(): List<AppInfo>

    @Query("SELECT * FROM app_info WHERE isSystemApp = 1 ORDER BY appName ASC")
    fun getSystemApps(): LiveData<List<AppInfo>>

    @Query("SELECT * FROM app_info WHERE isSystemApp = 0 ORDER BY appName ASC")
    fun getUserApps(): LiveData<List<AppInfo>>

    @Query("SELECT * FROM app_info WHERE appName LIKE '%' || :query || '%' OR packageName LIKE '%' || :query || '%' ORDER BY appName ASC")
    fun searchApps(query: String): LiveData<List<AppInfo>>

    @Query("UPDATE app_info SET isInternetAllowed = :allowed WHERE uid = :uid")
    suspend fun setInternetAllowed(uid: Int, allowed: Boolean)

    @Query("UPDATE app_info SET wifiAllowed = :allowed WHERE uid = :uid")
    suspend fun setWifiAllowed(uid: Int, allowed: Boolean)

    @Query("UPDATE app_info SET mobileAllowed = :allowed WHERE uid = :uid")
    suspend fun setMobileAllowed(uid: Int, allowed: Boolean)

    @Query("UPDATE app_info SET backgroundAllowed = :allowed WHERE uid = :uid")
    suspend fun setBackgroundAllowed(uid: Int, allowed: Boolean)

    @Query("DELETE FROM app_info")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM app_info")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM app_info")
    fun getCountLive(): LiveData<Int>
}
