package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.AppLimitBackupItem
import com.stayfocused.app.domain.model.BlockedDomainBackupItem
import com.stayfocused.app.domain.model.EncryptedBackupEnvelope
import com.stayfocused.app.domain.model.ProfileBackupItem
import com.stayfocused.app.domain.model.SettingsBackupPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.GeneralSecurityException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsCryptoEngineTest {

    private lateinit var engine: SettingsCryptoEngine

    @Before
    fun setUp() {
        engine = SettingsCryptoEngine()
    }

    @Test
    fun testPayloadSerializationAndDeserialization() {
        val originalPayload = SettingsBackupPayload(
            version = 1,
            exportedAtTimestamp = 1727330000000L,
            appLimits = listOf(
                AppLimitBackupItem(
                    packageName = "com.instagram.android",
                    appName = "Instagram",
                    dailyTimeLimitMinutes = 45,
                    dailyLaunchLimit = 10,
                    isBlocked = false
                )
            ),
            blockedDomains = listOf(
                BlockedDomainBackupItem(
                    domain = "reddit.com",
                    isBlocked = true,
                    category = "social"
                )
            ),
            profiles = listOf(
                ProfileBackupItem(
                    id = 1,
                    name = "Deep Focus",
                    isStrictMode = true,
                    scheduleStartTime = "09:00",
                    scheduleEndTime = "17:00",
                    activeDaysMask = 31,
                    blockedPackages = listOf("com.instagram.android"),
                    blockedDomains = listOf("reddit.com")
                )
            )
        )

        val json = engine.serializePayload(originalPayload)
        val deserialized = engine.deserializePayload(json)

        assertEquals(originalPayload.version, deserialized.version)
        assertEquals(originalPayload.exportedAtTimestamp, deserialized.exportedAtTimestamp)
        assertEquals(1, deserialized.appLimits.size)
        assertEquals("com.instagram.android", deserialized.appLimits[0].packageName)
        assertEquals(45, deserialized.appLimits[0].dailyTimeLimitMinutes)
        assertEquals(1, deserialized.blockedDomains.size)
        assertEquals("reddit.com", deserialized.blockedDomains[0].domain)
        assertEquals(1, deserialized.profiles.size)
        assertEquals("Deep Focus", deserialized.profiles[0].name)
        assertEquals(listOf("com.instagram.android"), deserialized.profiles[0].blockedPackages)
        assertEquals(listOf("reddit.com"), deserialized.profiles[0].blockedDomains)
    }

    @Test
    fun testEncryptAndDecryptRoundTripWithCorrectPassword() {
        val plainText = "{\"testKey\":\"secretValue123\"}"
        val password = "SuperSecretPassword123!"

        val envelope = engine.encryptText(plainText, password)

        assertNotNull(envelope.saltBase64)
        assertNotNull(envelope.ivBase64)
        assertNotNull(envelope.ciphertextBase64)
        assertEquals(EncryptedBackupEnvelope.FORMAT_MAGIC, envelope.format)
        assertEquals(EncryptedBackupEnvelope.CURRENT_VERSION, envelope.version)
        assertEquals(SettingsCryptoEngine.PBKDF2_ITERATIONS, envelope.iterations)

        val decrypted = engine.decryptText(envelope, password)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun testAADTamperingFailsDecryption() {
        val plainText = "{\"testKey\":\"secretValue123\"}"
        val password = "SuperSecretPassword123!"

        val envelope = engine.encryptText(plainText, password)

        // Attacker tampers with version in envelope header
        val tamperedEnvelope = envelope.copy(version = 99)

        assertThrows(GeneralSecurityException::class.java) {
            engine.decryptText(tamperedEnvelope, password)
        }
    }

    @Test
    fun testDecryptWithWrongPasswordFails() {
        val plainText = "{\"testKey\":\"secretValue123\"}"
        val envelope = engine.encryptText(plainText, "CorrectPassword123")

        assertThrows(GeneralSecurityException::class.java) {
            engine.decryptText(envelope, "WrongPassword456")
        }
    }

    @Test
    fun testDecryptWithCorruptedCiphertextFails() {
        val plainText = "{\"testKey\":\"secretValue123\"}"
        val envelope = engine.encryptText(plainText, "CorrectPassword123")

        val corruptedEnvelope = envelope.copy(ciphertextBase64 = "CorruptedBytes==")

        assertThrows(Exception::class.java) {
            engine.decryptText(corruptedEnvelope, "CorrectPassword123")
        }
    }

    @Test
    fun testEnvelopeSerializationRoundTrip() {
        val envelope = EncryptedBackupEnvelope(
            format = EncryptedBackupEnvelope.FORMAT_MAGIC,
            version = EncryptedBackupEnvelope.CURRENT_VERSION,
            saltBase64 = "c2FsdDEyMzQ1Njc4OTA=",
            ivBase64 = "aXYxMjM0NTY3ODkw",
            ciphertextBase64 = "Y2lwaGVydGV4dA==",
            iterations = 100_000
        )

        val json = engine.serializeEnvelope(envelope)
        val parsed = engine.deserializeEnvelope(json)

        assertEquals(envelope, parsed)
    }

    @Test
    fun testDeserializeEnvelopeRejectsUnknownFormat() {
        val invalidJson = "{\"format\":\"unknown_app\",\"version\":1}"
        assertThrows(IllegalArgumentException::class.java) {
            engine.deserializeEnvelope(invalidJson)
        }
    }

    @Test
    fun testDeserializeEnvelopeRejectsUnsupportedVersion() {
        val invalidJson = "{\"format\":\"monk_mode_encrypted_backup\",\"version\":99,\"salt\":\"c2FsdA==\",\"iv\":\"aXY=\",\"ciphertext\":\"Y2lwaGVy\"}"
        assertThrows(IllegalArgumentException::class.java) {
            engine.deserializeEnvelope(invalidJson)
        }
    }
}
