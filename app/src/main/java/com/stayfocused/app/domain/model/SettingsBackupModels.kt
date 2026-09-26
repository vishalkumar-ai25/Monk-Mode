package com.stayfocused.app.domain.model

/**
 * Data representation of the decrypted settings payload exported or imported.
 */
data class SettingsBackupPayload(
    val version: Int = 1,
    val exportedAtTimestamp: Long = System.currentTimeMillis(),
    val appLimits: List<AppLimitBackupItem> = emptyList(),
    val blockedDomains: List<BlockedDomainBackupItem> = emptyList(),
    val profiles: List<ProfileBackupItem> = emptyList()
)

data class AppLimitBackupItem(
    val packageName: String,
    val appName: String,
    val dailyTimeLimitMinutes: Int = 0,
    val dailyLaunchLimit: Int = 0,
    val isBlocked: Boolean = false
)

data class BlockedDomainBackupItem(
    val domain: String,
    val isBlocked: Boolean = true,
    val category: String = "general"
)

data class ProfileBackupItem(
    val id: Long = 0,
    val name: String,
    val isStrictMode: Boolean = false,
    val scheduleStartTime: String? = null,
    val scheduleEndTime: String? = null,
    val activeDaysMask: Int = 0,
    val blockedPackages: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList()
)

/**
 * Serialized JSON wrapper holding encrypted data and cryptographic metadata (salt, iv).
 */
data class EncryptedBackupEnvelope(
    val format: String = FORMAT_MAGIC,
    val version: Int = CURRENT_VERSION,
    val saltBase64: String,
    val ivBase64: String,
    val ciphertextBase64: String,
    val iterations: Int = if (version >= CURRENT_VERSION) DEFAULT_PBKDF2_ITERATIONS else LEGACY_PBKDF2_ITERATIONS
) {
    companion object {
        const val FORMAT_MAGIC = "monk_mode_encrypted_backup"
        const val CURRENT_VERSION = 2
        const val LEGACY_PBKDF2_ITERATIONS = 10_000
        const val DEFAULT_PBKDF2_ITERATIONS = 210_000
    }
}

/**
 * Result returned when attempting to import settings into Monk Mode.
 */
sealed class SettingsImportResult {
    data class Success(
        val limitsImported: Int,
        val domainsImported: Int,
        val profilesImported: Int
    ) : SettingsImportResult()

    data object BlockedByStrictMode : SettingsImportResult()
    data class DecryptionFailed(val message: String) : SettingsImportResult()
    data class InvalidPayload(val message: String) : SettingsImportResult()
}
