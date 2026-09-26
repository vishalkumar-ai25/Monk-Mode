package com.stayfocused.app.util

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight storage for protection health diagnostics and watchdog tracking.
 */
class ProtectionPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var lastWatchdogRunTimestamp: Long
        get() = prefs.getLong(KEY_LAST_WATCHDOG_RUN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_WATCHDOG_RUN, value).apply()

    var lastRedAlertTimestamp: Long
        get() = prefs.getLong(KEY_LAST_RED_ALERT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RED_ALERT, value).apply()

    companion object {
        private const val PREFS_NAME = "stayfocused_protection_prefs"
        private const val KEY_LAST_WATCHDOG_RUN = "key_last_watchdog_run"
        private const val KEY_LAST_RED_ALERT = "key_last_red_alert"
    }
}
