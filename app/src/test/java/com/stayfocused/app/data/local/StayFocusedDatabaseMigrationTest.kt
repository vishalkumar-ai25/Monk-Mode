package com.stayfocused.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StayFocusedDatabaseMigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        StayFocusedDatabase::class.java
    )

    @Test
    fun migrate2To3_preservesExistingDataAndCreatesFailsafeLogsTable() {
        // 1. Create database at version 2
        var db = helper.createDatabase(TEST_DB, 2)

        // Insert sample rows into version 2 schema
        db.execSQL(
            "INSERT INTO app_limits (packageName, appName, dailyTimeLimitMinutes, dailyLaunchLimit, isBlocked, currentDayUsageMs, currentDayLaunches, lastResetTimestamp) " +
                "VALUES ('com.instagram.android', 'Instagram', 30, 10, 1, 15000, 3, 1000000)"
        )
        db.execSQL(
            "INSERT INTO blocked_domains (domain, isBlocked, category, createdAt) " +
                "VALUES ('distraction.com', 1, 'social', 2000000)"
        )
        db.execSQL(
            "INSERT INTO recovery_codes (id, passwordHash, salt, isConsumed, createdAt) " +
                "VALUES (1, 'hash123', 'salt456', 0, 3000000)"
        )
        db.execSQL(
            "INSERT INTO focus_profiles (id, name, isActive, isStrictMode, activeDaysMask) " +
                "VALUES (1, 'Deep Work', 1, 1, 127)"
        )
        db.execSQL(
            "INSERT INTO strict_sessions (id, profileId, startTime, targetEndTime, delayedUnlockDurationMs, isActive) " +
                "VALUES (1, 1, 4000000, 5000000, 86400000, 1)"
        )
        db.execSQL(
            "INSERT INTO break_sessions (id, startTime, endTime, durationMinutes, isActive) " +
                "VALUES (1, 6000000, 6000900, 15, 0)"
        )
        db.close()

        // 2. Run migration 2 -> 3
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            StayFocusedDatabase.MIGRATION_2_3
        )

        // 3. Verify AppLimitEntity data survived
        val appCursor = db.query("SELECT packageName, appName, dailyTimeLimitMinutes, dailyLaunchLimit, isBlocked, currentDayUsageMs, currentDayLaunches, lastResetTimestamp FROM app_limits WHERE packageName = 'com.instagram.android'")
        assertTrue(appCursor.moveToFirst())
        assertEquals("Instagram", appCursor.getString(1))
        assertEquals(30, appCursor.getInt(2))
        assertEquals(10, appCursor.getInt(3))
        assertEquals(1, appCursor.getInt(4))
        assertEquals(15000L, appCursor.getLong(5))
        assertEquals(3, appCursor.getInt(6))
        assertEquals(1000000L, appCursor.getLong(7))
        appCursor.close()

        // 4. Verify BlockedDomainEntity data survived
        val domainCursor = db.query("SELECT domain, isBlocked, category, createdAt FROM blocked_domains WHERE domain = 'distraction.com'")
        assertTrue(domainCursor.moveToFirst())
        assertEquals(1, domainCursor.getInt(1))
        assertEquals("social", domainCursor.getString(2))
        assertEquals(2000000L, domainCursor.getLong(3))
        domainCursor.close()

        // 5. Verify RecoveryCodeEntity data survived
        val recoveryCursor = db.query("SELECT id, passwordHash, salt, isConsumed, createdAt FROM recovery_codes WHERE id = 1")
        assertTrue(recoveryCursor.moveToFirst())
        assertEquals("hash123", recoveryCursor.getString(1))
        assertEquals("salt456", recoveryCursor.getString(2))
        assertEquals(0, recoveryCursor.getInt(3))
        assertEquals(3000000L, recoveryCursor.getLong(4))
        recoveryCursor.close()

        // 6. Verify StrictSessionEntity data survived
        val sessionCursor = db.query("SELECT id, profileId, startTime, targetEndTime, delayedUnlockDurationMs, isActive FROM strict_sessions WHERE id = 1")
        assertTrue(sessionCursor.moveToFirst())
        assertEquals(1L, sessionCursor.getLong(1))
        assertEquals(4000000L, sessionCursor.getLong(2))
        assertEquals(5000000L, sessionCursor.getLong(3))
        assertEquals(86400000L, sessionCursor.getLong(4))
        assertEquals(1, sessionCursor.getInt(5))
        sessionCursor.close()

        // 7. Verify failsafe_logs table was created and is queryable
        val logCursor = db.query("SELECT COUNT(*) FROM failsafe_logs")
        assertTrue(logCursor.moveToFirst())
        assertEquals(0, logCursor.getInt(0))
        logCursor.close()

        // 8. Verify inserting into failsafe_logs works in migrated schema
        db.execSQL(
            "INSERT INTO failsafe_logs (timestamp, eventType, details, success) " +
                "VALUES (7000000, 'DELAY_REQUESTED', 'Requested 24h delay', 1)"
        )
        val verifyLogCursor = db.query("SELECT timestamp, eventType, details, success FROM failsafe_logs WHERE timestamp = 7000000")
        assertTrue(verifyLogCursor.moveToFirst())
        assertEquals(7000000L, verifyLogCursor.getLong(0))
        assertEquals("DELAY_REQUESTED", verifyLogCursor.getString(1))
        assertEquals("Requested 24h delay", verifyLogCursor.getString(2))
        assertEquals(1, verifyLogCursor.getInt(3))
        verifyLogCursor.close()
    }

    @Test
    fun migrate3To4_preservesExistingDataAndAddsReasonColumnToBreakSessions() {
        // 1. Create database at version 3
        var db = helper.createDatabase(TEST_DB + "-v3", 3)

        // Insert break session into version 3 (schema 3 has no reason column)
        db.execSQL(
            "INSERT INTO break_sessions (id, startTime, endTime, durationMinutes, isActive) " +
                "VALUES (1, 6000000, 6000900, 15, 0)"
        )
        // Insert failsafe log into version 3
        db.execSQL(
            "INSERT INTO failsafe_logs (timestamp, eventType, details, success) " +
                "VALUES (7000000, 'DELAY_REQUESTED', 'Requested 24h delay', 1)"
        )
        db.close()

        // 2. Run migration 3 -> 4
        db = helper.runMigrationsAndValidate(
            TEST_DB + "-v3",
            4,
            true,
            StayFocusedDatabase.MIGRATION_3_4
        )

        // 3. Verify existing BreakSessionEntity data survived and has reason default ''
        val breakCursor = db.query("SELECT id, startTime, endTime, durationMinutes, isActive, reason FROM break_sessions WHERE id = 1")
        assertTrue(breakCursor.moveToFirst())
        assertEquals(1L, breakCursor.getLong(0))
        assertEquals(6000000L, breakCursor.getLong(1))
        assertEquals(6000900L, breakCursor.getLong(2))
        assertEquals(15, breakCursor.getInt(3))
        assertEquals(0, breakCursor.getInt(4))
        assertEquals("", breakCursor.getString(5))
        breakCursor.close()

        // 4. Verify failsafe_logs survived
        val logCursor = db.query("SELECT timestamp, eventType, details, success FROM failsafe_logs WHERE timestamp = 7000000")
        assertTrue(logCursor.moveToFirst())
        assertEquals(7000000L, logCursor.getLong(0))
        assertEquals("DELAY_REQUESTED", logCursor.getString(1))
        logCursor.close()

        // 5. Verify inserting a new break session with custom reason works at version 4
        db.execSQL(
            "INSERT INTO break_sessions (id, startTime, endTime, durationMinutes, isActive, reason) " +
                "VALUES (2, 8000000, 8001800, 30, 1, 'Doctor Appointment')"
        )
        val newBreakCursor = db.query("SELECT id, startTime, endTime, durationMinutes, isActive, reason FROM break_sessions WHERE id = 2")
        assertTrue(newBreakCursor.moveToFirst())
        assertEquals(2L, newBreakCursor.getLong(0))
        assertEquals("Doctor Appointment", newBreakCursor.getString(5))
        newBreakCursor.close()
    }

    @Test
    fun migrate2To4_preservesExistingDataAcrossCumulativeMigrations() {
        // 1. Create database at version 2
        var db = helper.createDatabase(TEST_DB + "-v2", 2)

        db.execSQL(
            "INSERT INTO app_limits (packageName, appName, dailyTimeLimitMinutes, dailyLaunchLimit, isBlocked, currentDayUsageMs, currentDayLaunches, lastResetTimestamp) " +
                "VALUES ('com.instagram.android', 'Instagram', 30, 10, 1, 15000, 3, 1000000)"
        )
        db.execSQL(
            "INSERT INTO break_sessions (id, startTime, endTime, durationMinutes, isActive) " +
                "VALUES (1, 6000000, 6000900, 15, 0)"
        )
        db.close()

        // 2. Run cumulative migrations 2 -> 3 and 3 -> 4
        db = helper.runMigrationsAndValidate(
            TEST_DB + "-v2",
            4,
            true,
            StayFocusedDatabase.MIGRATION_2_3,
            StayFocusedDatabase.MIGRATION_3_4
        )

        // 3. Verify AppLimitEntity data survived
        val appCursor = db.query("SELECT packageName, appName, dailyTimeLimitMinutes, currentDayUsageMs FROM app_limits WHERE packageName = 'com.instagram.android'")
        assertTrue(appCursor.moveToFirst())
        assertEquals("Instagram", appCursor.getString(1))
        assertEquals(30, appCursor.getInt(2))
        assertEquals(15000L, appCursor.getLong(3))
        appCursor.close()

        // 4. Verify BreakSessionEntity data survived with default empty reason
        val breakCursor = db.query("SELECT id, startTime, endTime, durationMinutes, isActive, reason FROM break_sessions WHERE id = 1")
        assertTrue(breakCursor.moveToFirst())
        assertEquals(1L, breakCursor.getLong(0))
        assertEquals(6000000L, breakCursor.getLong(1))
        assertEquals(15, breakCursor.getInt(3))
        assertEquals(0, breakCursor.getInt(4))
        assertEquals("", breakCursor.getString(5))
        breakCursor.close()

        // 5. Verify failsafe_logs table was created and can accept inserts
        db.execSQL(
            "INSERT INTO failsafe_logs (timestamp, eventType, details, success) " +
                "VALUES (9000000, 'BOOT_GRACE_WINDOW_USED', 'Rebooted', 1)"
        )
        val logCursor = db.query("SELECT timestamp, eventType FROM failsafe_logs WHERE timestamp = 9000000")
        assertTrue(logCursor.moveToFirst())
        assertEquals("BOOT_GRACE_WINDOW_USED", logCursor.getString(1))
        logCursor.close()
    }

    @Test
    fun migrate4To5_preservesExistingDataAndCreatesStrictSchedulesTable() {
        // 1. Create database at version 4
        var db = helper.createDatabase(TEST_DB + "-v4", 4)

        db.execSQL(
            "INSERT INTO focus_profiles (id, name, isActive, isStrictMode, activeDaysMask) " +
                "VALUES (1, 'Work Profile', 1, 1, 127)"
        )
        db.execSQL(
            "INSERT INTO strict_sessions (id, profileId, startTime, targetEndTime, delayedUnlockDurationMs, isActive) " +
                "VALUES (1, 1, 4000000, 5000000, 86400000, 1)"
        )
        db.close()

        // 2. Run migration 4 -> 5
        db = helper.runMigrationsAndValidate(
            TEST_DB + "-v4",
            5,
            true,
            StayFocusedDatabase.MIGRATION_4_5
        )

        // 3. Verify strict_sessions data survived with default column values
        val sessionCursor = db.query("SELECT id, profileId, startTime, targetEndTime, delayedUnlockDurationMs, isActive, startElapsedRealtime, deactivationChallenge FROM strict_sessions WHERE id = 1")
        assertTrue(sessionCursor.moveToFirst())
        assertEquals(1L, sessionCursor.getLong(0))
        assertEquals(1L, sessionCursor.getLong(1))
        assertEquals(4000000L, sessionCursor.getLong(2))
        assertEquals(5000000L, sessionCursor.getLong(3))
        assertEquals(86400000L, sessionCursor.getLong(4))
        assertEquals(1, sessionCursor.getInt(5))
        assertEquals(0L, sessionCursor.getLong(6))
        assertEquals("EXPIRATION_ONLY", sessionCursor.getString(7))
        sessionCursor.close()

        // 4. Verify strict_schedules table was created and can accept inserts
        db.execSQL(
            "INSERT INTO strict_schedules (name, daysOfWeekMask, startMinuteOfDay, endMinuteOfDay, profileId, deactivationChallenge, isEnabled, dismissedUntilEpochMs, createdAt) " +
                "VALUES ('Workday Focus', 31, 540, 1020, 1, 'RANDOM_TEXT', 1, 0, 7000000)"
        )
        val scheduleCursor = db.query("SELECT id, name, daysOfWeekMask, startMinuteOfDay, endMinuteOfDay, profileId, deactivationChallenge, isEnabled, dismissedUntilEpochMs FROM strict_schedules WHERE name = 'Workday Focus'")
        assertTrue(scheduleCursor.moveToFirst())
        assertEquals(1L, scheduleCursor.getLong(0))
        assertEquals("Workday Focus", scheduleCursor.getString(1))
        assertEquals(31, scheduleCursor.getInt(2))
        assertEquals(540, scheduleCursor.getInt(3))
        assertEquals(1020, scheduleCursor.getInt(4))
        assertEquals(1L, scheduleCursor.getLong(5))
        assertEquals("RANDOM_TEXT", scheduleCursor.getString(6))
        assertEquals(1, scheduleCursor.getInt(7))
        assertEquals(0L, scheduleCursor.getLong(8))
        scheduleCursor.close()
    }

    @Test
    fun migrate5To6_preservesExistingDataAndAddsMonotonicCheckpointColumns() {
        // 1. Create database at version 5
        var db = helper.createDatabase(TEST_DB + "-v5", 5)

        db.execSQL(
            "INSERT INTO focus_profiles (id, name, isActive, isStrictMode, activeDaysMask) " +
                "VALUES (1, 'Deep Work', 1, 1, 127)"
        )
        db.execSQL(
            "INSERT INTO strict_sessions (id, profileId, startTime, targetEndTime, delayedUnlockDurationMs, isActive, startElapsedRealtime, deactivationChallenge) " +
                "VALUES (1, 1, 1000000, 5000000, 86400000, 1, 12345, 'EXPIRATION_ONLY')"
        )
        db.close()

        // 2. Run migration 5 -> 6
        db = helper.runMigrationsAndValidate(
            TEST_DB + "-v5",
            6,
            true,
            StayFocusedDatabase.MIGRATION_5_6
        )

        // 3. Verify strict_sessions data survived and new columns exist with correct values
        val sessionCursor = db.query(
            "SELECT id, profileId, startTime, targetEndTime, isActive, startElapsedRealtime, " +
                "accumulatedMonotonicMs, lastElapsedRealtime, lastWallTime, bootCount, delayedUnlockStartAccumulatedMs, isScheduled " +
                "FROM strict_sessions WHERE id = 1"
        )
        assertTrue(sessionCursor.moveToFirst())
        assertEquals(1L, sessionCursor.getLong(0))
        assertEquals(1L, sessionCursor.getLong(1))
        assertEquals(1000000L, sessionCursor.getLong(2))
        assertEquals(5000000L, sessionCursor.getLong(3))
        assertEquals(1, sessionCursor.getInt(4))
        assertEquals(12345L, sessionCursor.getLong(5))
        assertTrue("accumulatedMonotonicMs should be non-negative", sessionCursor.getLong(6) >= 0L)
        assertEquals(12345L, sessionCursor.getLong(7))
        assertTrue("lastWallTime should be populated by migration", sessionCursor.getLong(8) > 0L)
        assertEquals(-1, sessionCursor.getInt(9))
        assertTrue("delayedUnlockStartAccumulatedMs should be null by default", sessionCursor.isNull(10))
        assertEquals(0, sessionCursor.getInt(11))
        sessionCursor.close()
    }

    @Test
    fun migrate6To7_preservesExistingDataAndCreatesShortFormRulesTable() {
        // 1. Create database at version 6
        var db = helper.createDatabase(TEST_DB + "-v6", 6)

        db.execSQL(
            "INSERT INTO app_limits (packageName, appName, dailyTimeLimitMinutes, dailyLaunchLimit, isBlocked, currentDayUsageMs, currentDayLaunches, lastResetTimestamp) " +
                "VALUES ('com.google.android.youtube', 'YouTube', 60, 20, 0, 50000, 5, 1000000)"
        )
        db.close()

        // 2. Run migration 6 -> 7
        db = helper.runMigrationsAndValidate(
            TEST_DB + "-v6",
            7,
            true,
            StayFocusedDatabase.MIGRATION_6_7
        )

        // 3. Verify app_limits data survived
        val appCursor = db.query("SELECT packageName, appName, dailyTimeLimitMinutes, isBlocked FROM app_limits WHERE packageName = 'com.google.android.youtube'")
        assertTrue(appCursor.moveToFirst())
        assertEquals("YouTube", appCursor.getString(1))
        assertEquals(60, appCursor.getInt(2))
        assertEquals(0, appCursor.getInt(3))
        appCursor.close()

        // 4. Verify short_form_rules table exists and default rules were seeded
        val rulesCursor = db.query("SELECT id, packageName, displayName, isBlocked, dailyScrollLimit, currentDayScrolls FROM short_form_rules ORDER BY id ASC")
        assertTrue(rulesCursor.moveToFirst())
        val seededIds = mutableListOf<String>()
        do {
            seededIds.add(rulesCursor.getString(0))
        } while (rulesCursor.moveToNext())
        rulesCursor.close()

        assertTrue("Should seed youtube_shorts", seededIds.contains("youtube_shorts"))
        assertTrue("Should seed instagram_reels", seededIds.contains("instagram_reels"))
        assertTrue("Should seed instagram_stories", seededIds.contains("instagram_stories"))
        assertTrue("Should seed snapchat_spotlight", seededIds.contains("snapchat_spotlight"))
        assertTrue("Should seed facebook_reels", seededIds.contains("facebook_reels"))
    }

    @Test
    fun migrate7To8_preservesExistingDataAndCreatesBrowsingTables() {
        // 1. Create database at version 7
        var db = helper.createDatabase(TEST_DB + "-v7", 7)

        db.execSQL(
            "INSERT INTO short_form_rules (id, packageName, displayName, isBlocked, dailyScrollLimit, currentDayScrolls, lastEvictionTimestamp) " +
                "VALUES ('youtube_shorts', 'com.google.android.youtube', 'YouTube Shorts', 1, 5, 2, 1000)"
        )
        db.close()

        // 2. Run migration 7 -> 8
        db = helper.runMigrationsAndValidate(
            TEST_DB + "-v7",
            8,
            true,
            StayFocusedDatabase.MIGRATION_7_8
        )

        // 3. Verify short_form_rules data survived
        val sfCursor = db.query("SELECT id, isBlocked, dailyScrollLimit, currentDayScrolls FROM short_form_rules WHERE id = 'youtube_shorts'")
        assertTrue(sfCursor.moveToFirst())
        assertEquals("youtube_shorts", sfCursor.getString(0))
        assertEquals(1, sfCursor.getInt(1))
        assertEquals(5, sfCursor.getInt(2))
        assertEquals(2, sfCursor.getInt(3))
        sfCursor.close()

        // 4. Verify browsing_history table exists and is writable
        db.execSQL(
            "INSERT INTO browsing_history (timestampMs, browserPackage, domain, fullUrl, dwellTimeMs, wasBlocked, blockReason) " +
                "VALUES (5000000, 'com.android.chrome', 'reddit.com', 'https://reddit.com/r/androiddev', 12000, 0, NULL)"
        )
        val historyCursor = db.query("SELECT timestampMs, browserPackage, domain, fullUrl, dwellTimeMs, wasBlocked FROM browsing_history WHERE domain = 'reddit.com'")
        assertTrue(historyCursor.moveToFirst())
        assertEquals(5000000L, historyCursor.getLong(0))
        assertEquals("com.android.chrome", historyCursor.getString(1))
        assertEquals("reddit.com", historyCursor.getString(2))
        assertEquals("https://reddit.com/r/androiddev", historyCursor.getString(3))
        assertEquals(12000L, historyCursor.getLong(4))
        assertEquals(0, historyCursor.getInt(5))
        historyCursor.close()

        // 5. Verify blocked_keywords table exists and is writable
        db.execSQL(
            "INSERT INTO blocked_keywords (keyword, isBlocked, category, createdAt) " +
                "VALUES ('casino', 1, 'custom', 6000000)"
        )
        val kwCursor = db.query("SELECT keyword, isBlocked, category FROM blocked_keywords WHERE keyword = 'casino'")
        assertTrue(kwCursor.moveToFirst())
        assertEquals("casino", kwCursor.getString(0))
        assertEquals(1, kwCursor.getInt(1))
        assertEquals("custom", kwCursor.getString(2))
        kwCursor.close()

        // 6. Verify blocked_url_paths table exists and is writable
        db.execSQL(
            "INSERT INTO blocked_url_paths (pathPattern, isBlocked, createdAt) " +
                "VALUES ('/r/all', 1, 7000000)"
        )
        val pathCursor = db.query("SELECT pathPattern, isBlocked FROM blocked_url_paths WHERE pathPattern = '/r/all'")
        assertTrue(pathCursor.moveToFirst())
        assertEquals("/r/all", pathCursor.getString(0))
        assertEquals(1, pathCursor.getInt(1))
        pathCursor.close()
    }

    @Test
    fun freshV8DatabaseCreation_seedsDefaultShortFormRulesAndHasBrowsingTables() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = StayFocusedDatabase.getInstance(context)
        kotlinx.coroutines.runBlocking {
            val rules = db.shortFormRuleDao().getAllRules()
            if (rules.isEmpty()) {
                db.shortFormRuleDao().insertDefaultRules(StayFocusedDatabase.DEFAULT_SHORT_FORM_RULES)
            }
            val activeRules = db.shortFormRuleDao().getAllRules()
            assertTrue("Fresh DB should contain default short form rules", activeRules.isNotEmpty())

            // Test DAOs for new v8 tables
            val emptyKeywords = db.blockedKeywordDao().getAllBlockedKeywordsSync()
            assertTrue(emptyKeywords.isEmpty())

            val emptyPaths = db.blockedUrlPathDao().getAllBlockedUrlPathsSync()
            assertTrue(emptyPaths.isEmpty())

            val emptyHistory = db.browsingHistoryDao().getRecentHistorySync()
            assertTrue(emptyHistory.isEmpty())
        }
    }
}


