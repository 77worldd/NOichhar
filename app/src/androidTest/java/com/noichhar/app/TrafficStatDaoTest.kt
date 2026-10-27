package com.noichhar.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noichhar.app.database.NOichharDatabase
import com.noichhar.app.database.entity.TrafficStat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrafficStatDaoTest {
    @Test fun aggregatesOnlyRecordedEventsWithinTheSelectedWindow() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), NOichharDatabase::class.java).build()
        try {
            val now = System.currentTimeMillis()
            fun row(uid: Int, down: Long, up: Long, requests: Long, blocked: Long, metered: Boolean, age: Long) =
                TrafficStat(appUid = uid, packageName = "app.$uid", appName = "App $uid", bytesDown = down,
                    bytesUp = up, connections = requests, blocked = blocked, isMetered = metered, timestamp = now - age)
            val dao = db.trafficStatDao()
            dao.insertAll(listOf(
                row(1, 100, 20, 3, 1, false, 30 * 60_000L),
                row(2, 50, 10, 2, 2, true, 2 * 3_600_000L),
                row(1, 0, 0, 1, 0, false, 6L * 24 * 3_600_000L),
                row(3, 400, 0, 1, 1, false, 8L * 24 * 3_600_000L)
            ))
            val oneHour = now - 3_600_000L
            val totals = dao.totalsSince(oneHour)
            assertEquals(120L, totals.unmetered); assertEquals(0L, totals.metered); assertEquals(120L, totals.overall)
            val counts = dao.activitySince(oneHour)
            assertEquals(2L, counts.allowed); assertEquals(1L, counts.blocked)
            val daily = dao.totalsSince(now - 24 * 3_600_000L)
            assertEquals(180L, daily.overall); assertEquals(60L, daily.metered)
            val top = dao.topConnections(now - 24 * 3_600_000L)
            assertEquals(listOf(1, 2), top.map { it.appUid })
            assertEquals(3L, dao.activityBuckets(oneHour, now, 12 * 60_000L).sumOf { it.count })
            dao.deleteOlderThan(now - 7L * 24 * 3_600_000L)
            assertEquals(6L, dao.topConnections(0).sumOf { it.totalConn })
        } finally { db.close() }
    }
}
