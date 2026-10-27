package com.noichhar.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.noichhar.app.database.dao.*
import com.noichhar.app.database.entity.*

@Database(
    entities = [AppInfo::class, DnsLog::class, BlocklistEntry::class, ConnectionLog::class, TrafficStat::class],
    version = 2,
    exportSchema = false
)
abstract class NOichharDatabase : RoomDatabase() {

    abstract fun appInfoDao(): AppInfoDao
    abstract fun dnsLogDao(): DnsLogDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun connectionLogDao(): ConnectionLogDao
    abstract fun trafficStatDao(): TrafficStatDao

    companion object {
        // Preserve v1 app rules, blocklists and DNS logs on upgrade. Existing DNS
        // events become anonymous aggregates; byte totals begin with v2 tracking.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `traffic_stat` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `appUid` INTEGER NOT NULL, `packageName` TEXT NOT NULL, `appName` TEXT NOT NULL,
                    `bytesDown` INTEGER NOT NULL, `bytesUp` INTEGER NOT NULL, `connections` INTEGER NOT NULL,
                    `blocked` INTEGER NOT NULL, `isMetered` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_traffic_stat_timestamp` ON `traffic_stat` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_traffic_stat_appUid` ON `traffic_stat` (`appUid`)")
                db.execSQL("""INSERT INTO traffic_stat (appUid, packageName, appName, bytesDown, bytesUp, connections, blocked, isMetered, timestamp)
                    SELECT COALESCE(appUid, -1), '', COALESCE(appName, 'Unknown'), 0, 0, 1,
                    CASE WHEN isBlocked = 1 THEN 1 ELSE 0 END, 0, timestamp FROM dns_log""")
            }
        }

        @Volatile
        private var INSTANCE: NOichharDatabase? = null

        fun getInstance(context: Context): NOichharDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NOichharDatabase::class.java,
                    "noichhar_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
