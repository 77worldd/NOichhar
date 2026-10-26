package com.noichhar.app.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.noichhar.app.database.entity.BlocklistEntry

@Dao
interface BlocklistDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BlocklistEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlocklistEntry)

    @Delete
    suspend fun delete(entry: BlocklistEntry)

    @Query("SELECT * FROM blocklist ORDER BY domain ASC")
    fun getAll(): LiveData<List<BlocklistEntry>>

    @Query("SELECT * FROM blocklist")
    suspend fun getAllSync(): List<BlocklistEntry>

    @Query("SELECT COUNT(*) FROM blocklist")
    fun getCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM blocklist")
    suspend fun getCountSync(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM blocklist WHERE domain = :domain)")
    suspend fun isDomainBlocked(domain: String): Boolean

    @Query("SELECT * FROM blocklist WHERE isWildcard = 1")
    suspend fun getWildcardEntries(): List<BlocklistEntry>

    @Query("SELECT * FROM blocklist WHERE isUserAdded = 1 ORDER BY domain ASC")
    fun getUserAddedEntries(): LiveData<List<BlocklistEntry>>

    @Query("DELETE FROM blocklist WHERE isUserAdded = 0")
    suspend fun deleteNonUserEntries()

    @Query("DELETE FROM blocklist WHERE domain = :domain AND isUserAdded = 1")
    suspend fun deleteUserDomain(domain: String)

    @Query("DELETE FROM blocklist")
    suspend fun deleteAll()
}
