package com.noichhar.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noichhar.app.database.NOichharDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun migrationPreservesRulesLogsAndBackfillsAnonymousCounts() = runBlocking {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE app_info (uid INTEGER NOT NULL, packageName TEXT NOT NULL, appName TEXT NOT NULL, isSystemApp INTEGER NOT NULL, isInternetAllowed INTEGER NOT NULL, wifiAllowed INTEGER NOT NULL, mobileAllowed INTEGER NOT NULL, backgroundAllowed INTEGER NOT NULL, PRIMARY KEY(uid))")
            db.execSQL("CREATE TABLE blocklist (domain TEXT NOT NULL, source TEXT NOT NULL, isWildcard INTEGER NOT NULL, addedTimestamp INTEGER NOT NULL, isUserAdded INTEGER NOT NULL, PRIMARY KEY(domain))")
            db.execSQL("CREATE TABLE dns_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, domain TEXT NOT NULL, queryType TEXT NOT NULL, responseIp TEXT, isBlocked INTEGER NOT NULL, blockReason TEXT, responseTimeMs INTEGER NOT NULL, timestamp INTEGER NOT NULL, appUid INTEGER, appName TEXT)")
            db.execSQL("CREATE TABLE connection_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, appUid INTEGER NOT NULL, appName TEXT NOT NULL, destinationIp TEXT NOT NULL, destinationPort INTEGER NOT NULL, protocol TEXT NOT NULL, isBlocked INTEGER NOT NULL, timestamp INTEGER NOT NULL)")
            db.execSQL("INSERT INTO blocklist VALUES ('ads.example.com','user',0,123,1)")
            db.execSQL("INSERT INTO dns_log (domain,queryType,responseIp,isBlocked,blockReason,responseTimeMs,timestamp,appUid,appName) VALUES ('ads.example.com','A',NULL,1,'rule',12,123,10001,'App')")
            db.execSQL("PRAGMA user_version = 1")
        }
        val db = Room.databaseBuilder(context, NOichharDatabase::class.java, name)
            .addMigrations(NOichharDatabase.MIGRATION_1_2).build()
        try {
            assertEquals(1, db.blocklistDao().getUserEntriesSync().size)
            assertEquals(1L, db.dnsLogDao().getTotalCountSync())
            val stats = db.trafficStatDao().activitySince(0)
            assertEquals(1L, stats.blocked)
            assertEquals(0L, stats.allowed)
        } finally { db.close() }
    }
}
