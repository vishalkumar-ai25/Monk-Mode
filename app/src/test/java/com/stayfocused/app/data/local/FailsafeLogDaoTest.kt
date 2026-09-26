package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.dao.FailsafeLogDao
import com.stayfocused.app.data.local.entities.FailsafeEventType
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FailsafeLogDaoTest {

    private lateinit var db: StayFocusedDatabase
    private lateinit var dao: FailsafeLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.failsafeLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertLog_and_getAllLogsSync_returnsDescendingOrder() = runBlocking {
        val log1 = FailsafeLogEntity(
            timestamp = 1000L,
            eventType = FailsafeEventType.DELAY_REQUESTED.name,
            details = "Requested 24h delay",
            success = true
        )
        val log2 = FailsafeLogEntity(
            timestamp = 2000L,
            eventType = FailsafeEventType.RECOVERY_CODE_ENTERED.name,
            details = "Recovery code entered",
            success = false
        )
        val log3 = FailsafeLogEntity(
            timestamp = 3000L,
            eventType = FailsafeEventType.BOOT_GRACE_WINDOW_USED.name,
            details = "Boot grace period active",
            success = true
        )

        dao.insertLog(log1)
        dao.insertLog(log2)
        dao.insertLog(log3)

        val logs = dao.getAllLogsSync()
        assertEquals(3, logs.size)
        // Verify newest first
        assertEquals(3000L, logs[0].timestamp)
        assertEquals(FailsafeEventType.BOOT_GRACE_WINDOW_USED.name, logs[0].eventType)
        assertEquals(2000L, logs[1].timestamp)
        assertEquals(FailsafeEventType.RECOVERY_CODE_ENTERED.name, logs[1].eventType)
        assertEquals(1000L, logs[2].timestamp)
        assertEquals(FailsafeEventType.DELAY_REQUESTED.name, logs[2].eventType)
    }

    @Test
    fun getLogCount_returnsAccurateCount() = runBlocking {
        assertEquals(0, dao.getLogCount())

        dao.insertLog(
            FailsafeLogEntity(
                timestamp = System.currentTimeMillis(),
                eventType = FailsafeEventType.DELAY_REQUESTED.name,
                details = "Test",
                success = true
            )
        )
        assertEquals(1, dao.getLogCount())

        dao.insertLog(
            FailsafeLogEntity(
                timestamp = System.currentTimeMillis() + 100,
                eventType = FailsafeEventType.DELAY_CANCELLED.name,
                details = "Cancelled",
                success = true
            )
        )
        assertEquals(2, dao.getLogCount())
    }

    @Test
    fun insertLog_storesFieldsAccurately() = runBlocking {
        val id = dao.insertLog(
            FailsafeLogEntity(
                timestamp = 1700000000000L,
                eventType = FailsafeEventType.RECOVERY_CODE_ENTERED.name,
                details = "Failed attempt with bad code",
                success = false
            )
        )
        assertTrue(id > 0)

        val logs = dao.getAllLogsSync()
        val entry = logs.first()
        assertEquals(1700000000000L, entry.timestamp)
        assertEquals(FailsafeEventType.RECOVERY_CODE_ENTERED.name, entry.eventType)
        assertEquals("Failed attempt with bad code", entry.details)
        assertFalse(entry.success)
    }

    @Test
    fun getAllLogsFlow_emitsLiveUpdates() = runBlocking {
        val initial = dao.getAllLogs().first()
        assertTrue(initial.isEmpty())

        dao.insertLog(
            FailsafeLogEntity(
                timestamp = 5000L,
                eventType = FailsafeEventType.DELAY_REQUESTED.name,
                details = "Flow test",
                success = true
            )
        )

        val updated = dao.getAllLogs().first()
        assertEquals(1, updated.size)
        assertEquals("Flow test", updated[0].details)
    }
}
