package com.stayfocused.app.domain

import android.util.Base64
import com.stayfocused.app.domain.model.AppLimitBackupItem
import com.stayfocused.app.domain.model.BlockedDomainBackupItem
import com.stayfocused.app.domain.model.EncryptedBackupEnvelope
import com.stayfocused.app.domain.model.ProfileBackupItem
import com.stayfocused.app.domain.model.SettingsBackupPayload
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Kotlin cryptographic and serialization engine for settings backup and restore.
 * Uses AES-256-GCM authenticated encryption and PBKDF2 key derivation.
 */
class SettingsCryptoEngine(
    private val secureRandom: SecureRandom = SecureRandom()
) {

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_LENGTH_BITS = 256
        const val PBKDF2_ITERATIONS = 210_000
        const val LEGACY_PBKDF2_ITERATIONS = 10_000
        private const val SALT_LENGTH_BYTES = 16
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    /**
     * Encrypts plaintext string using AES-256-GCM with a PBKDF2-derived key from [password].
     */
    fun encryptText(
        plainText: String,
        password: String,
        iterations: Int = PBKDF2_ITERATIONS
    ): EncryptedBackupEnvelope {
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }

        val key = deriveKey(password.toCharArray(), salt, iterations)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        return EncryptedBackupEnvelope(
            format = EncryptedBackupEnvelope.FORMAT_MAGIC,
            version = EncryptedBackupEnvelope.CURRENT_VERSION,
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            iterations = iterations
        )
    }

    /**
     * Decrypts ciphertext from [envelope] using [password]. Throws GeneralSecurityException if
     * authentication tag or key fails.
     */
    fun decryptText(envelope: EncryptedBackupEnvelope, password: String): String {
        if (envelope.format != EncryptedBackupEnvelope.FORMAT_MAGIC) {
            throw IllegalArgumentException("Unsupported backup format: ${envelope.format}")
        }

        val salt = Base64.decode(envelope.saltBase64, Base64.DEFAULT)
        val iv = Base64.decode(envelope.ivBase64, Base64.DEFAULT)
        val ciphertext = Base64.decode(envelope.ciphertextBase64, Base64.DEFAULT)

        val iterations = if (envelope.iterations > 0) {
            envelope.iterations
        } else if (envelope.version >= 2) {
            PBKDF2_ITERATIONS
        } else {
            LEGACY_PBKDF2_ITERATIONS
        }

        val key = deriveKey(password.toCharArray(), salt, iterations)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)

        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    /**
     * Serializes a SettingsBackupPayload into a JSON string.
     */
    fun serializePayload(payload: SettingsBackupPayload): String {
        val root = JSONObject().apply {
            put("version", payload.version)
            put("exportedAt", payload.exportedAtTimestamp)

            val appLimitsArray = JSONArray()
            for (limit in payload.appLimits) {
                appLimitsArray.put(JSONObject().apply {
                    put("packageName", limit.packageName)
                    put("appName", limit.appName)
                    put("dailyTimeLimitMinutes", limit.dailyTimeLimitMinutes)
                    put("dailyLaunchLimit", limit.dailyLaunchLimit)
                    put("isBlocked", limit.isBlocked)
                })
            }
            put("appLimits", appLimitsArray)

            val blockedDomainsArray = JSONArray()
            for (domain in payload.blockedDomains) {
                blockedDomainsArray.put(JSONObject().apply {
                    put("domain", domain.domain)
                    put("isBlocked", domain.isBlocked)
                    put("category", domain.category)
                })
            }
            put("blockedDomains", blockedDomainsArray)

            val profilesArray = JSONArray()
            for (profile in payload.profiles) {
                profilesArray.put(JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("isStrictMode", profile.isStrictMode)
                    if (profile.scheduleStartTime != null) put("scheduleStartTime", profile.scheduleStartTime)
                    if (profile.scheduleEndTime != null) put("scheduleEndTime", profile.scheduleEndTime)
                    put("activeDaysMask", profile.activeDaysMask)

                    val pkgs = JSONArray()
                    profile.blockedPackages.forEach { pkgs.put(it) }
                    put("blockedPackages", pkgs)

                    val domains = JSONArray()
                    profile.blockedDomains.forEach { domains.put(it) }
                    put("blockedDomains", domains)
                })
            }
            put("profiles", profilesArray)
        }

        return root.toString()
    }

    /**
     * Deserializes a JSON string into a SettingsBackupPayload.
     */
    fun deserializePayload(json: String): SettingsBackupPayload {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())

        val appLimits = mutableListOf<AppLimitBackupItem>()
        val appLimitsArray = root.optJSONArray("appLimits")
        if (appLimitsArray != null) {
            for (i in 0 until appLimitsArray.length()) {
                val obj = appLimitsArray.getJSONObject(i)
                appLimits.add(
                    AppLimitBackupItem(
                        packageName = obj.getString("packageName"),
                        appName = obj.optString("appName", ""),
                        dailyTimeLimitMinutes = obj.optInt("dailyTimeLimitMinutes", 0),
                        dailyLaunchLimit = obj.optInt("dailyLaunchLimit", 0),
                        isBlocked = obj.optBoolean("isBlocked", false)
                    )
                )
            }
        }

        val blockedDomains = mutableListOf<BlockedDomainBackupItem>()
        val blockedDomainsArray = root.optJSONArray("blockedDomains")
        if (blockedDomainsArray != null) {
            for (i in 0 until blockedDomainsArray.length()) {
                val obj = blockedDomainsArray.getJSONObject(i)
                blockedDomains.add(
                    BlockedDomainBackupItem(
                        domain = obj.getString("domain"),
                        isBlocked = obj.optBoolean("isBlocked", true),
                        category = obj.optString("category", "general")
                    )
                )
            }
        }

        val profiles = mutableListOf<ProfileBackupItem>()
        val profilesArray = root.optJSONArray("profiles")
        if (profilesArray != null) {
            for (i in 0 until profilesArray.length()) {
                val obj = profilesArray.getJSONObject(i)

                val pkgs = mutableListOf<String>()
                val pkgsArray = obj.optJSONArray("blockedPackages")
                if (pkgsArray != null) {
                    for (j in 0 until pkgsArray.length()) {
                        pkgs.add(pkgsArray.getString(j))
                    }
                }

                val domains = mutableListOf<String>()
                val domainsArray = obj.optJSONArray("blockedDomains")
                if (domainsArray != null) {
                    for (j in 0 until domainsArray.length()) {
                        domains.add(domainsArray.getString(j))
                    }
                }

                profiles.add(
                    ProfileBackupItem(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        isStrictMode = obj.optBoolean("isStrictMode", false),
                        scheduleStartTime = if (obj.has("scheduleStartTime")) obj.getString("scheduleStartTime") else null,
                        scheduleEndTime = if (obj.has("scheduleEndTime")) obj.getString("scheduleEndTime") else null,
                        activeDaysMask = obj.optInt("activeDaysMask", 0),
                        blockedPackages = pkgs,
                        blockedDomains = domains
                    )
                )
            }
        }

        return SettingsBackupPayload(
            version = version,
            exportedAtTimestamp = exportedAt,
            appLimits = appLimits,
            blockedDomains = blockedDomains,
            profiles = profiles
        )
    }

    /**
     * Serializes an EncryptedBackupEnvelope into JSON.
     */
    fun serializeEnvelope(envelope: EncryptedBackupEnvelope): String {
        return JSONObject().apply {
            put("format", envelope.format)
            put("version", envelope.version)
            put("salt", envelope.saltBase64)
            put("iv", envelope.ivBase64)
            put("ciphertext", envelope.ciphertextBase64)
            put("iterations", envelope.iterations)
        }.toString()
    }

    /**
     * Deserializes a JSON string into an EncryptedBackupEnvelope.
     */
    fun deserializeEnvelope(json: String): EncryptedBackupEnvelope {
        val root = JSONObject(json)
        val format = root.getString("format")
        if (format != EncryptedBackupEnvelope.FORMAT_MAGIC) {
            throw IllegalArgumentException("Unsupported backup format: $format")
        }

        val version = root.optInt("version", 1)
        val defaultIterations = if (version >= 2) {
            EncryptedBackupEnvelope.DEFAULT_PBKDF2_ITERATIONS
        } else {
            EncryptedBackupEnvelope.LEGACY_PBKDF2_ITERATIONS
        }
        val iterations = root.optInt("iterations", defaultIterations)

        return EncryptedBackupEnvelope(
            format = format,
            version = version,
            saltBase64 = root.getString("salt"),
            ivBase64 = root.getString("iv"),
            ciphertextBase64 = root.getString("ciphertext"),
            iterations = iterations
        )
    }
}
