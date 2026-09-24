package com.stayfocused.app.util

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Cryptographic utility for generating high-entropy recovery codes and
 * hashing them using PBKDF2WithHmacSHA256 with random salts and constant-time verification.
 */
object RecoveryCodeHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16

    private val secureRandom = SecureRandom()
    private val CHAR_POOL = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray() // Exclude ambiguous chars (I, O, 0, 1)

    /**
     * Generates a 24-character high-entropy recovery code formatted as XXXXXX-XXXXXX-XXXXXX-XXXXXX.
     */
    fun generateRecoveryCode(): String {
        val raw = StringBuilder()
        for (i in 0 until 24) {
            val idx = secureRandom.nextInt(CHAR_POOL.size)
            raw.append(CHAR_POOL[idx])
        }
        return raw.chunked(6).joinToString("-")
    }

    /**
     * Generates a 16-character high-entropy recovery code formatted as XXXX-XXXX-XXXX-XXXX.
     */
    fun generate16CharRecoveryCode(): String {
        val raw = StringBuilder()
        for (i in 0 until 16) {
            val idx = secureRandom.nextInt(CHAR_POOL.size)
            raw.append(CHAR_POOL[idx])
        }
        return raw.chunked(4).joinToString("-")
    }

    /**
     * Generates a random 16-byte salt encoded as a hex string.
     */
    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt.toHexString()
    }

    /**
     * Derives a PBKDF2WithHmacSHA256 hash from the recovery code and hex-encoded salt.
     */
    fun hashRecoveryCode(code: String, saltHex: String): String {
        val normalizedCode = normalizeCode(code)
        val saltBytes = saltHex.hexToByteArray()
        val spec = PBEKeySpec(normalizedCode.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        val hash = factory.generateSecret(spec).encoded
        return hash.toHexString()
    }

    /**
     * Verifies an entered recovery code against the stored salt and expected hash using constant-time comparison.
     */
    fun verify(enteredCode: String, saltHex: String, expectedHashHex: String): Boolean {
        return try {
            val computedHashHex = hashRecoveryCode(enteredCode, saltHex)
            MessageDigest.isEqual(
                computedHashHex.toByteArray(Charsets.UTF_8),
                expectedHashHex.toByteArray(Charsets.UTF_8)
            )
        } catch (e: Exception) {
            false
        }
    }

    private fun normalizeCode(code: String): String {
        return code.replace("-", "").trim().uppercase()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun String.hexToByteArray(): ByteArray {
        check(length % 2 == 0) { "Hex string must have an even length" }
        return chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
