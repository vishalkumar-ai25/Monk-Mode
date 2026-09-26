package com.stayfocused.app.strict

import android.content.Context
import android.content.SharedPreferences

enum class StrictUnlockMethod(val displayName: String, val description: String) {
    HARDCORE(
        "Hardcore (Wait for Timer)",
        "Zero early exit allowed. Strictly locked until the timer expires."
    ),
    PIN(
        "PIN / Passcode",
        "Enter a 4-digit PIN to exit early. Can be set by a friend or self."
    ),
    TYPING_PHRASE(
        "Typing Friction Challenge",
        "Type an anti-distraction pledge manually. Copy-pasting is blocked."
    ),
    TIME_DELAYED(
        "Time-Delayed Unlock",
        "Wait a cooldown delay (15 mins) before unlock is finalized."
    ),
    EMERGENCY_CODE(
        "Emergency Recovery Code",
        "Redeem single-use 16-character cryptographic recovery code."
    )
}

/**
 * Preferences for configuring Strict Mode behavior, unlock methods, and tamper barriers.
 */
class StrictPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var unlockMethod: StrictUnlockMethod
        get() {
            val name = prefs.getString(KEY_UNLOCK_METHOD, StrictUnlockMethod.HARDCORE.name)
            return try {
                StrictUnlockMethod.valueOf(name ?: StrictUnlockMethod.HARDCORE.name)
            } catch (e: Exception) {
                StrictUnlockMethod.HARDCORE
            }
        }
        set(value) = prefs.edit().putString(KEY_UNLOCK_METHOD, value.name).apply()

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var pinSalt: String?
        get() = prefs.getString(KEY_PIN_SALT, null)
        set(value) = prefs.edit().putString(KEY_PIN_SALT, value).apply()

    var typingPhrase: String
        get() = prefs.getString(KEY_TYPING_PHRASE, DEFAULT_PHRASE) ?: DEFAULT_PHRASE
        set(value) = prefs.edit().putString(KEY_TYPING_PHRASE, value).apply()

    var blockSettings: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_SETTINGS, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_SETTINGS, value).apply()

    var preventUninstall: Boolean
        get() = prefs.getBoolean(KEY_PREVENT_UNINSTALL, true)
        set(value) = prefs.edit().putBoolean(KEY_PREVENT_UNINSTALL, value).apply()

    var lastDurationMinutes: Int
        get() = prefs.getInt(KEY_LAST_DURATION_MINUTES, 60)
        set(value) = prefs.edit().putInt(KEY_LAST_DURATION_MINUTES, value).apply()

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).apply()
    }

    companion object {
        private const val PREFS_NAME = "stayfocused_strict_preferences"
        private const val KEY_UNLOCK_METHOD = "key_strict_unlock_method"
        private const val KEY_PIN_HASH = "key_strict_pin_hash"
        private const val KEY_PIN_SALT = "key_strict_pin_salt"
        private const val KEY_TYPING_PHRASE = "key_strict_typing_phrase"
        private const val KEY_BLOCK_SETTINGS = "key_strict_block_settings"
        private const val KEY_PREVENT_UNINSTALL = "key_strict_prevent_uninstall"
        private const val KEY_LAST_DURATION_MINUTES = "key_strict_last_duration_minutes"

        const val DEFAULT_PHRASE = "I choose my long term goals over instant gratification and distraction."
    }
}
