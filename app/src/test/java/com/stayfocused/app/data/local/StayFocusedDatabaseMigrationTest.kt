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
}

