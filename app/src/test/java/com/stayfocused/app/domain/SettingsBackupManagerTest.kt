package com.stayfocused.app.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.domain.model.SettingsImportResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase
    private lateinit var backupManager: SettingsBackupManager
    private val testPassword = "MonkSecurePassword2026!"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupManager = SettingsBackupManager(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExportEncryptedBackupGeneratesValidEncryptedEnvelope() = runBlocking {
        // Insert sample settings
        db.appLimitDao().upsertAppLimit(
            AppLimitEntity(
                packageName = "com.twitter.android",
                appName = "X",
                dailyTimeLimitMinutes = 30,
                dailyLaunchLimit = 10,
                isBlocked = false
            )
        )
        db.blockedDomainDao().upsertBlockedDomain(
            BlockedDomainEntity(domain = "twitter.com", isBlocked = true, category = "social")
        )
        val profileId = db.focusProfileDao().upsertProfile(
            FocusProfileEntity(id = 1, name = "Deep Work", isStrictMode = false)
        )
        db.focusProfileDao().insertBlockedPackages(
            listOf(ProfileBlockedPackageEntity(profileId, "com.twitter.android"))
        )
        db.focusProfileDao().insertBlockedDomains(
            listOf(ProfileBlockedDomainEntity(profileId, "twitter.com"))
        )

        // Sensitive data that must NOT be exported
        db.recoveryCodeDao().upsertRecoveryCode(
            RecoveryCodeEntity(id = 1, passwordHash = "SECRET-HASH-1234", salt = "SALT", isConsumed = false)
        )

        val encryptedEnvelopeJson = backupManager.exportEncryptedBackup(testPassword)
        assertNotNull(encryptedEnvelopeJson)
        assertTrue(encryptedEnvelopeJson.contains("monk_mode_encrypted_backup"))
        // Check that plaintext sensitive strings are NOT in the raw encrypted envelope
        assertFalse(encryptedEnvelopeJson.contains("SECRET-HASH-1234"))
        assertFalse(encryptedEnvelopeJson.contains("com.twitter.android"))
    }

    @Test
    fun testImportEncryptedBackupSuccessfullyRestoresData() = runBlocking {
        // 1. Populate DB and export
        db.appLimitDao().upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                dailyTimeLimitMinutes = 45,
                dailyLaunchLimit = 5,
                isBlocked = true
            )
        )
        db.blockedDomainDao().upsertBlockedDomain(
            BlockedDomainEntity(domain = "instagram.com", isBlocked = true, category = "social")
        )
        val profileId = db.focusProfileDao().upsertProfile(
            FocusProfileEntity(id = 1, name = "Evening Wind Down")
        )
        db.focusProfileDao().insertBlockedPackages(
            listOf(ProfileBlockedPackageEntity(profileId, "com.instagram.android"))
        )

        val encryptedBackup = backupManager.exportEncryptedBackup(testPassword)

        // 2. Clear target database
        db.clearAllTables()
        assertEquals(0, db.appLimitDao().getAllAppLimitsSync().size)
        assertEquals(0, db.blockedDomainDao().getAllBlockedDomainsSync().size)
        assertEquals(0, db.focusProfileDao().getAllProfilesSync().size)

        // 3. Import backup
        val result = backupManager.importEncryptedBackup(encryptedBackup, testPassword)
        assertTrue(result is SettingsImportResult.Success)
        val success = result as SettingsImportResult.Success
        assertEquals(1, success.limitsImported)
        assertEquals(1, success.domainsImported)
        assertEquals(1, success.profilesImported)

        // 4. Verify restored entities
        val restoredLimits = db.appLimitDao().getAllAppLimitsSync()
        assertEquals(1, restoredLimits.size)
        assertEquals("com.instagram.android", restoredLimits[0].packageName)
        assertEquals(45, restoredLimits[0].dailyTimeLimitMinutes)
        assertTrue(restoredLimits[0].isBlocked)

        val restoredDomains = db.blockedDomainDao().getAllBlockedDomainsSync()
        assertEquals(1, restoredDomains.size)
        assertEquals("instagram.com", restoredDomains[0].domain)

        val restoredProfiles = db.focusProfileDao().getAllProfilesWithRulesSync()
        assertEquals(1, restoredProfiles.size)
        assertEquals("Evening Wind Down", restoredProfiles[0].profile.name)
        assertEquals(1, restoredProfiles[0].blockedPackages.size)
        assertEquals("com.instagram.android", restoredProfiles[0].blockedPackages[0].packageName)
    }

    @Test
    fun testImportEncryptedBackupBlockedWhenStrictModeIsActive() = runBlocking {
        // Seed export
        db.appLimitDao().upsertAppLimit(
            AppLimitEntity(packageName = "com.instagram.android", appName = "Instagram")
        )
        val encryptedBackup = backupManager.exportEncryptedBackup(testPassword)

        // Activate Strict Mode
        val profileId = db.focusProfileDao().upsertProfile(FocusProfileEntity(id = 1, name = "Strict Profile"))
        db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 1,
                profileId = profileId,
                startTime = System.currentTimeMillis() - 1000L,
                targetEndTime = System.currentTimeMillis() + 3600_000L,
                isActive = true
            )
        )

        // Attempt import while strict mode active
        val result = backupManager.importEncryptedBackup(encryptedBackup, testPassword)
        assertEquals(SettingsImportResult.BlockedByStrictMode, result)
    }

    @Test
    fun testImportEncryptedBackupPreservesCurrentDayUsageCounters() = runBlocking {
        // 1. Device currently has Instagram with 40 mins usage today
        db.appLimitDao().upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                dailyTimeLimitMinutes = 60,
                currentDayUsageMs = 40 * 60 * 1000L,
                currentDayLaunches = 7
            )
        )

        // 2. Backup has Instagram with 0 mins usage and new limit of 30 mins
        val backupPayload = backupManager.exportEncryptedBackup(testPassword)

        // Modify local usage further to 50 mins
        db.appLimitDao().upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                dailyTimeLimitMinutes = 60,
                currentDayUsageMs = 50 * 60 * 1000L,
                currentDayLaunches = 9
            )
        )

        // 3. Restore backup
        val result = backupManager.importEncryptedBackup(backupPayload, testPassword)
        assertTrue(result is SettingsImportResult.Success)

        // 4. Verify usage was NOT wiped to 0
        val updatedLimit = db.appLimitDao().getAllAppLimitsSync().first { it.packageName == "com.instagram.android" }
        assertEquals(50 * 60 * 1000L, updatedLimit.currentDayUsageMs)
        assertEquals(9, updatedLimit.currentDayLaunches)
    }

    @Test
    fun testImportWithWrongPasswordReturnsDecryptionFailed() = runBlocking {
        db.appLimitDao().upsertAppLimit(
            AppLimitEntity(packageName = "com.instagram.android", appName = "Instagram")
        )
        val encryptedBackup = backupManager.exportEncryptedBackup(testPassword)

        val result = backupManager.importEncryptedBackup(encryptedBackup, "WrongPassword!")
        assertTrue(result is SettingsImportResult.DecryptionFailed)
    }

    @Test
    fun testImportWithCorruptedEnvelopeReturnsInvalidPayload() = runBlocking {
        val corruptedJson = "{\"invalid\":\"not an envelope\"}"
        val result = backupManager.importEncryptedBackup(corruptedJson, testPassword)
        assertTrue(result is SettingsImportResult.InvalidPayload)
    }
}
