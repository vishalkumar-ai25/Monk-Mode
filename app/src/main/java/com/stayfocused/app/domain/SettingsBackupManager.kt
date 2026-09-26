package com.stayfocused.app.domain

import androidx.room.withTransaction
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import com.stayfocused.app.domain.model.AppLimitBackupItem
import com.stayfocused.app.domain.model.BlockedDomainBackupItem
import com.stayfocused.app.domain.model.ProfileBackupItem
import com.stayfocused.app.domain.model.SettingsBackupPayload
import com.stayfocused.app.domain.model.SettingsImportResult
import java.security.GeneralSecurityException

/**
 * Coordinates encrypted export and import of Monk Mode settings.
 * Strictly excludes recovery codes and active strict mode sessions from backups,
 * and enforces anti-tamper guards to prevent import during active Strict Mode.
 */
class SettingsBackupManager(
    private val database: StayFocusedDatabase,
    private val cryptoEngine: SettingsCryptoEngine = SettingsCryptoEngine()
) {

    /**
     * Exports all user app limits, blocked domains, and focus profiles to an AES-256-GCM encrypted envelope.
     */
    suspend fun exportEncryptedBackup(password: String): String {
        val appLimits = database.appLimitDao().getAllAppLimitsSync().map {
            AppLimitBackupItem(
                packageName = it.packageName,
                appName = it.appName,
                dailyTimeLimitMinutes = it.dailyTimeLimitMinutes,
                dailyLaunchLimit = it.dailyLaunchLimit,
                isBlocked = it.isBlocked
            )
        }

        val blockedDomains = database.blockedDomainDao().getAllBlockedDomainsSync().map {
            BlockedDomainBackupItem(
                domain = it.domain,
                isBlocked = it.isBlocked,
                category = it.category
            )
        }

        val profiles = database.focusProfileDao().getAllProfilesWithRulesSync().map { profileWithRules ->
            ProfileBackupItem(
                id = profileWithRules.profile.id,
                name = profileWithRules.profile.name,
                isStrictMode = profileWithRules.profile.isStrictMode,
                scheduleStartTime = profileWithRules.profile.scheduleStartTime,
                scheduleEndTime = profileWithRules.profile.scheduleEndTime,
                activeDaysMask = profileWithRules.profile.activeDaysMask,
                blockedPackages = profileWithRules.blockedPackages.map { it.packageName },
                blockedDomains = profileWithRules.blockedDomains.map { it.domain }
            )
        }

        val payload = SettingsBackupPayload(
            version = 1,
            exportedAtTimestamp = System.currentTimeMillis(),
            appLimits = appLimits,
            blockedDomains = blockedDomains,
            profiles = profiles
        )

        val payloadJson = cryptoEngine.serializePayload(payload)
        val envelope = cryptoEngine.encryptText(payloadJson, password)
        return cryptoEngine.serializeEnvelope(envelope)
    }

    /**
     * Imports and restores settings from an encrypted backup envelope.
     * Enforces anti-tamper validation:
     * 1. Rejects import if Strict Mode is currently active.
     * 2. Preserves current day screen time and launch counts to prevent quota resetting.
     */
    suspend fun importEncryptedBackup(envelopeJson: String, password: String): SettingsImportResult {
        // Anti-Tamper Rule 1: Cannot import settings while Strict Mode is active
        val activeStrict = database.strictSessionDao().getActiveStrictSessionSync()
        if (activeStrict != null) {
            return SettingsImportResult.BlockedByStrictMode
        }

        val envelope = try {
            cryptoEngine.deserializeEnvelope(envelopeJson)
        } catch (e: Exception) {
            return SettingsImportResult.InvalidPayload(e.message ?: "Invalid backup envelope format")
        }

        val decryptedPayloadJson = try {
            cryptoEngine.decryptText(envelope, password)
        } catch (e: GeneralSecurityException) {
            return SettingsImportResult.DecryptionFailed(e.message ?: "Authentication failed (wrong password or corrupted backup)")
        } catch (e: Exception) {
            return SettingsImportResult.DecryptionFailed(e.message ?: "Failed to decrypt backup")
        }

        val payload = try {
            cryptoEngine.deserializePayload(decryptedPayloadJson)
        } catch (e: Exception) {
            return SettingsImportResult.InvalidPayload(e.message ?: "Invalid settings payload structure")
        }

        // Apply backup inside transaction
        database.withTransaction {
            val appLimitDao = database.appLimitDao()
            val existingLimits = appLimitDao.getAllAppLimitsSync().associateBy { it.packageName }

            for (item in payload.appLimits) {
                val existing = existingLimits[item.packageName]
                val entity = AppLimitEntity(
                    packageName = item.packageName,
                    appName = item.appName,
                    dailyTimeLimitMinutes = item.dailyTimeLimitMinutes,
                    dailyLaunchLimit = item.dailyLaunchLimit,
                    isBlocked = item.isBlocked,
                    // Anti-Tamper Rule 2: Keep today's consumed screen time and launches
                    currentDayUsageMs = existing?.currentDayUsageMs ?: 0L,
                    currentDayLaunches = existing?.currentDayLaunches ?: 0,
                    lastResetTimestamp = existing?.lastResetTimestamp ?: System.currentTimeMillis()
                )
                appLimitDao.upsertAppLimit(entity)
            }

            val domainDao = database.blockedDomainDao()
            for (domainItem in payload.blockedDomains) {
                domainDao.upsertBlockedDomain(
                    BlockedDomainEntity(
                        domain = domainItem.domain,
                        isBlocked = domainItem.isBlocked,
                        category = domainItem.category
                    )
                )
            }

            val profileDao = database.focusProfileDao()
            for (profileItem in payload.profiles) {
                val profileId = profileDao.upsertProfile(
                    FocusProfileEntity(
                        id = profileItem.id,
                        name = profileItem.name,
                        isStrictMode = profileItem.isStrictMode,
                        scheduleStartTime = profileItem.scheduleStartTime,
                        scheduleEndTime = profileItem.scheduleEndTime,
                        activeDaysMask = profileItem.activeDaysMask
                    )
                )

                profileDao.clearBlockedPackages(profileId)
                if (profileItem.blockedPackages.isNotEmpty()) {
                    profileDao.insertBlockedPackages(
                        profileItem.blockedPackages.map { ProfileBlockedPackageEntity(profileId, it) }
                    )
                }

                profileDao.clearBlockedDomains(profileId)
                if (profileItem.blockedDomains.isNotEmpty()) {
                    profileDao.insertBlockedDomains(
                        profileItem.blockedDomains.map { ProfileBlockedDomainEntity(profileId, it) }
                    )
                }
            }
        }

        return SettingsImportResult.Success(
            limitsImported = payload.appLimits.size,
            domainsImported = payload.blockedDomains.size,
            profilesImported = payload.profiles.size
        )
    }
}
