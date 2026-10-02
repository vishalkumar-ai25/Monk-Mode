package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.dao.FocusProfileDao
import com.stayfocused.app.data.local.dao.StrictScheduleDao
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StrictScheduleDaoTest {

    private lateinit var db: StayFocusedDatabase
    private lateinit var scheduleDao: StrictScheduleDao
    private lateinit var profileDao: FocusProfileDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduleDao = db.strictScheduleDao()
        profileDao = db.focusProfileDao()

        // Create a foreign key target profile
        runBlocking {
            profileDao.upsertProfile(
                FocusProfileEntity(
                    id = 1,
                    name = "Deep Work",
                    isActive = true,
                    isStrictMode = true,
                    activeDaysMask = 127
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQuerySchedule_returnsCorrectValues() = runBlocking {
        val schedule = StrictScheduleEntity(
            name = "Morning Focus",
            daysOfWeekMask = 31, // Mon-Fri
            startMinuteOfDay = 540, // 09:00
            endMinuteOfDay = 720,  // 12:00
            profileId = 1,
            deactivationChallenge = "RANDOM_TEXT",
            isEnabled = true,
            dismissedUntilEpochMs = 0L,
            createdAt = 1000L
        )

        val id = scheduleDao.insertSchedule(schedule)
        assertTrue(id > 0)

        val retrieved = scheduleDao.getScheduleByIdSync(id)
        assertNotNull(retrieved)
        assertEquals("Morning Focus", retrieved?.name)
        assertEquals(31, retrieved?.daysOfWeekMask)
        assertEquals(540, retrieved?.startMinuteOfDay)
        assertEquals(720, retrieved?.endMinuteOfDay)
        assertEquals("RANDOM_TEXT", retrieved?.deactivationChallenge)
        assertTrue(retrieved?.isEnabled == true)
        assertEquals(0L, retrieved?.dismissedUntilEpochMs)
    }

    @Test
    fun getEnabledSchedulesSync_returnsOnlyEnabled() = runBlocking {
        scheduleDao.insertSchedule(
            StrictScheduleEntity(
                name = "Sched 1",
                daysOfWeekMask = 1,
                startMinuteOfDay = 100,
                endMinuteOfDay = 200,
                profileId = 1,
                isEnabled = true
            )
        )
        scheduleDao.insertSchedule(
            StrictScheduleEntity(
                name = "Sched 2",
                daysOfWeekMask = 1,
                startMinuteOfDay = 300,
                endMinuteOfDay = 400,
                profileId = 1,
                isEnabled = false
            )
        )

        val enabled = scheduleDao.getEnabledSchedulesSync()
        assertEquals(1, enabled.size)
        assertEquals("Sched 1", enabled[0].name)
    }

    @Test
    fun setScheduleEnabled_togglesState() = runBlocking {
        val id = scheduleDao.insertSchedule(
            StrictScheduleEntity(
                name = "Toggle Test",
                daysOfWeekMask = 1,
                startMinuteOfDay = 100,
                endMinuteOfDay = 200,
                profileId = 1,
                isEnabled = true
            )
        )

        scheduleDao.setScheduleEnabled(id, false)
        val disabled = scheduleDao.getScheduleByIdSync(id)
        assertFalse(disabled?.isEnabled ?: true)

        scheduleDao.setScheduleEnabled(id, true)
        val enabledAgain = scheduleDao.getScheduleByIdSync(id)
        assertTrue(enabledAgain?.isEnabled ?: false)
    }

    @Test
    fun setDismissedUntil_updatesDismissedTimestamp() = runBlocking {
        val id = scheduleDao.insertSchedule(
            StrictScheduleEntity(
                name = "Dismiss Test",
                daysOfWeekMask = 1,
                startMinuteOfDay = 100,
                endMinuteOfDay = 200,
                profileId = 1
            )
        )

        scheduleDao.setDismissedUntil(id, 9999999L)
        val updated = scheduleDao.getScheduleByIdSync(id)
        assertEquals(9999999L, updated?.dismissedUntilEpochMs)
    }

    @Test
    fun deleteSchedule_removesRow() = runBlocking {
        val id = scheduleDao.insertSchedule(
            StrictScheduleEntity(
                name = "Delete Test",
                daysOfWeekMask = 1,
                startMinuteOfDay = 100,
                endMinuteOfDay = 200,
                profileId = 1
            )
        )

        assertNotNull(scheduleDao.getScheduleByIdSync(id))
        scheduleDao.deleteSchedule(id)
        assertNull(scheduleDao.getScheduleByIdSync(id))
    }

    @Test
    fun getAllSchedules_flowEmitsUpdates() = runBlocking {
        val flow = scheduleDao.getAllSchedules()
        assertEquals(0, flow.first().size)

        scheduleDao.insertSchedule(
            StrictScheduleEntity(
                name = "Flow Test",
                daysOfWeekMask = 1,
                startMinuteOfDay = 100,
                endMinuteOfDay = 200,
                profileId = 1
            )
        )

        assertEquals(1, flow.first().size)
    }
}
