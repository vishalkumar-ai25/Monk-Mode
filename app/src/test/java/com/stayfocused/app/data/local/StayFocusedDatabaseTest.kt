package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.util.RecoveryCodeHasher
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
    fun testFocusProfileDaoWithJunctionTablesAndFastJoin() = runBlocking {
        val dao = db.focusProfileDao()

        val profile = FocusProfileEntity(
            name = "Work Session",
            isActive = true,
            isStrictMode = true
        )
        val profileId = dao.upsertProfile(profile)
        assertTrue(profileId > 0)

        // Insert junction table relations
        dao.insertBlockedPackages(
            listOf(
                ProfileBlockedPackageEntity(profileId, "com.twitter.android"),
                ProfileBlockedPackageEntity(profileId, "com.facebook.katana")
            )
        )
        dao.insertBlockedDomains(
            listOf(
                ProfileBlockedDomainEntity(profileId, "twitter.com"),
                ProfileBlockedDomainEntity(profileId, "facebook.com")
            )
        )

        // Hot path indexed join queries (< 10ms budget)
        val activePackages = dao.getActiveBlockedPackages()
        assertEquals(2, activePackages.size)
        assertTrue(activePackages.contains("com.twitter.android"))
        assertTrue(activePackages.contains("com.facebook.katana"))

        val activeDomains = dao.getActiveBlockedDomains()
        assertEquals(2, activeDomains.size)
        assertTrue(activeDomains.contains("twitter.com"))
        assertTrue(activeDomains.contains("facebook.com"))

        // Full profile with rules relation query
        val profileWithRules = dao.getProfileWithRules(profileId).first()
        assertNotNull(profileWithRules)
        assertEquals(2, profileWithRules?.blockedPackages?.size)
        assertEquals(2, profileWithRules?.blockedDomains?.size)

        // Deactivating profile excludes it from hot path queries
        dao.setProfileActive(profileId, false)
        assertEquals(0, dao.getActiveBlockedPackages().size)
        assertEquals(0, dao.getActiveBlockedDomains().size)
    }

    @Test
    fun testStrictSessionDaoWithForeignKey() = runBlocking {
        val profileDao = db.focusProfileDao()
        val sessionDao = db.strictSessionDao()
        val now = System.currentTimeMillis()

        // Create parent profile first (foreign key requirement)
        val profileId = profileDao.upsertProfile(FocusProfileEntity(name = "Strict Study", isActive = true))

        val session = StrictSessionEntity(
            profileId = profileId,
            startTime = now,
            targetEndTime = now + 7200000L,
            isActive = true
        )
        sessionDao.insertSession(session)

        val active = sessionDao.getActiveStrictSessionSync()
        assertNotNull(active)
        assertEquals(profileId, active?.profileId)
        assertTrue(active?.isActive == true)

        sessionDao.deactivateAllSessions()
        assertNull(sessionDao.getActiveStrictSessionSync())
    }

    @Test
    fun testRecoveryCodeDaoWithPbkdf2Salt() = runBlocking {
        val dao = db.recoveryCodeDao()

        val recoveryCode = RecoveryCodeHasher.generateRecoveryCode()
        val salt = RecoveryCodeHasher.generateSalt()
        val hash = RecoveryCodeHasher.hashRecoveryCode(recoveryCode, salt)

        val entity = RecoveryCodeEntity(
            passwordHash = hash,
            salt = salt
        )
        dao.upsertRecoveryCode(entity)

        val retrieved = dao.getRecoveryCodeSync()
        assertNotNull(retrieved)
        assertEquals(hash, retrieved?.passwordHash)
        assertEquals(salt, retrieved?.salt)
        assertFalse(retrieved?.isConsumed == true)

        // Verify valid code
        assertTrue(RecoveryCodeHasher.verify(recoveryCode, retrieved!!.salt, retrieved.passwordHash))
        // Verify invalid code fails
        assertFalse(RecoveryCodeHasher.verify("WRONG-CODE-1234-5678", retrieved.salt, retrieved.passwordHash))

        dao.markConsumed()
        val consumed = dao.getRecoveryCodeSync()
        assertTrue(consumed?.isConsumed == true)
    }

    @Test
    fun testSuppressedNotificationDaoCrud() = runBlocking {
        val dao = db.suppressedNotificationDao()

        val item1 = com.stayfocused.app.data.local.entities.SuppressedNotificationEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            title = "New Direct Message",
            contentSnippet = "Hey, check this reel out!",
            postTimestamp = 1000L,
            isViewed = false
        )
        val item2 = com.stayfocused.app.data.local.entities.SuppressedNotificationEntity(
            packageName = "com.twitter.android",
            appName = "X",
            title = "Trending Now",
            contentSnippet = "Breaking news in tech",
            postTimestamp = 2000L,
            isViewed = false
        )

        dao.insert(item1)
        dao.insert(item2)

        val all = dao.getAll().first()
        assertEquals(2, all.size)
        // Ordered by postTimestamp DESC
        assertEquals("X", all[0].appName)
        assertEquals("Instagram", all[1].appName)

        val unviewedCount = dao.getUnviewedCount().first()
        assertEquals(2, unviewedCount)

        dao.markAllAsViewed()
        val unviewedAfter = dao.getUnviewedCount().first()
        assertEquals(0, unviewedAfter)

        dao.clearOlderThan(1500L)
        val remaining = dao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals("X", remaining[0].appName)

        dao.clearAll()
        val emptyList = dao.getAll().first()
        assertTrue(emptyList.isEmpty())
    }

    @Test
    fun testBreakSessionDaoCrud() = runBlocking {
        val dao = db.breakSessionDao()

        val session = com.stayfocused.app.data.local.entities.BreakSessionEntity(
            startTime = 1000L,
            endTime = 5000L,
            durationMinutes = 5,
            isActive = true
        )

        dao.upsertBreak(session)

        val retrieved = dao.getActiveBreakSync()
        assertNotNull(retrieved)
        assertEquals(1000L, retrieved?.startTime)
        assertEquals(5000L, retrieved?.endTime)
        assertTrue(retrieved?.isActive == true)

        dao.deactivateBreak()
        val deactivated = dao.getActiveBreakSync()
        assertNull(deactivated)
    }
}
