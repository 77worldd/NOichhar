package com.noichhar.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.noichhar.app.database.dao.*
import com.noichhar.app.database.entity.*

@Database(
    entities = [AppInfo::class, DnsLog::class, BlocklistEntry::class, ConnectionLog::class],
    version = 1,
    exportSchema = false
)
abstract class NOichharDatabase : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao
    abstract fun dnsLogDao(): DnsLogDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun connectionLogDao(): ConnectionLogDao

    companion object {
        @Volatile
        private var INSTANCE: NOichharDatabase? = null

        fun getInstance(context: Context): NOichharDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NOichharDatabase::class.java,
                    "noichhar_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
