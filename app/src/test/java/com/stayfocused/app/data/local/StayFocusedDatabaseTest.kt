package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StayFocusedDatabaseTest {

    private lateinit var db: StayFocusedDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAppLimitDaoCrudAndReset() = runBlocking {
        val dao = db.appLimitDao()

        val app = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyTimeLimitMinutes = 30,
            dailyLaunchLimit = 10,
            currentDayUsageMs = 15000L,
            currentDayLaunches = 3
        )
        dao.upsertAppLimit(app)

        val retrieved = dao.getAppLimitSync("com.instagram.android")
        assertNotNull(retrieved)
        assertEquals("Instagram", retrieved?.appName)
        assertEquals(30, retrieved?.dailyTimeLimitMinutes)
        assertEquals(15000L, retrieved?.currentDayUsageMs)

        // Update usage and launches
        dao.updateUsageAndLaunches("com.instagram.android", 25000L, 5)
        val updated = dao.getAppLimitSync("com.instagram.android")
        assertEquals(25000L, updated?.currentDayUsageMs)
        assertEquals(5, updated?.currentDayLaunches)

        // Reset daily usage
        val resetTime = System.currentTimeMillis()
        dao.resetDailyUsage(resetTime)
        val reset = dao.getAppLimitSync("com.instagram.android")
        assertEquals(0L, reset?.currentDayUsageMs)
        assertEquals(0, reset?.currentDayLaunches)
        assertEquals(resetTime, reset?.lastResetTimestamp)
    }

    @Test
    fun testBlockedDomainDao() = runBlocking {
        val dao = db.blockedDomainDao()

        val domain = BlockedDomainEntity(domain = "reddit.com", isBlocked = true)
        dao.upsertBlockedDomain(domain)

        assertTrue(dao.isDomainBlocked("reddit.com"))
        assertFalse(dao.isDomainBlocked("wikipedia.org"))

        val allBlocked = dao.getAllBlockedDomains().first()
        assertEquals(1, allBlocked.size)
        assertEquals("reddit.com", allBlocked[0].domain)

        dao.deleteBlockedDomain("reddit.com")
        assertFalse(dao.isDomainBlocked("reddit.com"))
    }

    @Test
    fun testFocusProfileDao() = runBlocking {
        val dao = db.focusProfileDao()

        val profile = FocusProfileEntity(
            name = "Work Session",
            isActive = true,
            isStrictMode = true,
            blockedPackagesJson = "[\"com.twitter.android\"]"
        )
        val id = dao.upsertProfile(profile)
        assertTrue(id > 0)

        val activeProfiles = dao.getActiveProfilesSync()
        assertEquals(1, activeProfiles.size)
        assertEquals("Work Session", activeProfiles[0].name)
        assertTrue(activeProfiles[0].isStrictMode)

        dao.setProfileActive(id, false)
        assertEquals(0, dao.getActiveProfilesSync().size)
    }

    @Test
    fun testStrictSessionDao() = runBlocking {
        val dao = db.strictSessionDao()
        val now = System.currentTimeMillis()

        val session = StrictSessionEntity(
            profileId = 1L,
            startTime = now,
            targetEndTime = now + 7200000L,
            isActive = true
        )
        dao.insertSession(session)

        val active = dao.getActiveStrictSessionSync()
        assertNotNull(active)
        assertEquals(1L, active?.profileId)
        assertTrue(active?.isActive == true)

        dao.deactivateAllSessions()
        assertNull(dao.getActiveStrictSessionSync())
    }

    @Test
    fun testRecoveryCodeDao() = runBlocking {
        val dao = db.recoveryCodeDao()

        val code = RecoveryCodeEntity(
            hashedCode = "aabbcc112233"
        )
        dao.upsertRecoveryCode(code)

        val retrieved = dao.getRecoveryCodeSync()
        assertNotNull(retrieved)
        assertEquals("aabbcc112233", retrieved?.hashedCode)
        assertFalse(retrieved?.isConsumed == true)

        dao.markConsumed()
        val consumed = dao.getRecoveryCodeSync()
        assertTrue(consumed?.isConsumed == true)
    }
}
