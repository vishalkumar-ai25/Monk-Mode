package com.stayfocused.app.data.local

import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.GeofenceProfileEntity
import com.stayfocused.app.data.local.entities.NotificationBlockRuleEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.data.local.entities.UnlockEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitySchemaTest {

    @Test
    fun testAppLimitEntityDefaults() {
        val entity = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram"
        )
        assertEquals("com.instagram.android", entity.packageName)
        assertEquals("Instagram", entity.appName)
        assertEquals(0, entity.dailyTimeLimitMinutes)
        assertEquals(0, entity.dailyLaunchLimit)
        assertFalse(entity.isBlocked)
        assertEquals(0L, entity.currentDayUsageMs)
        assertEquals(0, entity.currentDayLaunches)
        assertTrue(entity.lastResetTimestamp > 0)
    }

    @Test
    fun testBlockedDomainEntityDefaults() {
        val entity = BlockedDomainEntity(
            domain = "reddit.com"
        )
        assertEquals("reddit.com", entity.domain)
        assertTrue(entity.isBlocked)
        assertEquals("general", entity.category)
        assertTrue(entity.createdAt > 0)
    }

    @Test
    fun testFocusProfileEntityDefaults() {
        val entity = FocusProfileEntity(
            name = "Deep Work"
        )
        assertEquals(0L, entity.id)
        assertEquals("Deep Work", entity.name)
        assertFalse(entity.isActive)
        assertFalse(entity.isStrictMode)
        assertEquals("[]", entity.blockedPackagesJson)
        assertEquals("[]", entity.blockedDomainsJson)
        assertNull(entity.scheduleStartTime)
        assertNull(entity.scheduleEndTime)
        assertEquals(0, entity.activeDaysMask)
    }

    @Test
    fun testStrictSessionEntityDefaults() {
        val now = System.currentTimeMillis()
        val entity = StrictSessionEntity(
            profileId = 1L,
            startTime = now,
            targetEndTime = now + 3600000L
        )
        assertEquals(0L, entity.id)
        assertEquals(1L, entity.profileId)
        assertEquals(now, entity.startTime)
        assertEquals(now + 3600000L, entity.targetEndTime)
        assertNull(entity.delayedUnlockRequestTime)
        assertEquals(24 * 60 * 60 * 1000L, entity.delayedUnlockDurationMs)
        assertTrue(entity.isActive)
    }

    @Test
    fun testRecoveryCodeEntity() {
        val now = System.currentTimeMillis()
        val entity = RecoveryCodeEntity(
            hashedCode = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )
        assertEquals(1, entity.id)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", entity.hashedCode)
        assertFalse(entity.isConsumed)
        assertTrue(entity.createdAt > 0)
    }

    @Test
    fun testPostMvpSchemaStubs() {
        val unlock = UnlockEventEntity(timestamp = 1000L, durationMs = 5000L)
        assertEquals(1000L, unlock.timestamp)
        assertEquals(5000L, unlock.durationMs)

        val geofence = GeofenceProfileEntity(profileId = 1L, latitude = 37.7749, longitude = -122.4194, radiusMeters = 100f)
        assertEquals(1L, geofence.profileId)
        assertEquals(37.7749, geofence.latitude, 0.0001)

        val notifRule = NotificationBlockRuleEntity(packageName = "com.whatsapp")
        assertEquals("com.whatsapp", notifRule.packageName)
        assertTrue(notifRule.blockAll)
        assertNull(notifRule.filterRegex)
    }
}
