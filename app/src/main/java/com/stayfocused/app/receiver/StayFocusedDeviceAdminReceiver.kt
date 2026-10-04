package com.stayfocused.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stayfocused.app.BuildConfig

/**
 * DeviceAdminReceiver providing anti-uninstallation protection for Stay Focused.
 * When enabled in the release build variant, device admin prevents standard user uninstallation.
 * In debug builds, anti-tamper is disabled to permit developer uninstallation without friction.
 */
class StayFocusedDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "StayFocusedDeviceAdmin"
        const val PREFS_NAME = "stayfocused_protection_prefs"
        const val KEY_STRICT_ACTIVE = "key_strict_active_cached"
        const val KEY_STRICT_END_TIME = "key_strict_end_time_cached"

        @Volatile
        var isStrictActiveCached: Boolean = false

        @Volatile
        var cachedStrictEndTimeMs: Long = 0L

        fun setStrictActiveCached(isActive: Boolean, endTimeMs: Long = 0L, context: Context? = null) {
            isStrictActiveCached = isActive
            cachedStrictEndTimeMs = endTimeMs
            if (context != null) {
                try {
                    val appContext = context.applicationContext ?: context
                    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_STRICT_ACTIVE, isActive)
                        .putLong(KEY_STRICT_END_TIME, endTimeMs)
                        .apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist strict active cache", e)
                }
            }
        }

        fun isStrictActive(context: Context): Boolean {
            val now = System.currentTimeMillis()
            if (isStrictActiveCached) {
                if (cachedStrictEndTimeMs > 0L && now >= cachedStrictEndTimeMs) {
                    setStrictActiveCached(false, 0L, context)
                    return false
                }
                return true
            }
            return try {
                val appContext = context.applicationContext ?: context
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isActive = prefs.getBoolean(KEY_STRICT_ACTIVE, false)
                if (!isActive) return false
                val endTime = prefs.getLong(KEY_STRICT_END_TIME, 0L)
                if (endTime > 0L && now >= endTime) {
                    setStrictActiveCached(false, 0L, context)
                    false
                } else {
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetStrictActiveForTesting(context: Context? = null) {
            isStrictActiveCached = false
            cachedStrictEndTimeMs = 0L
            context?.let {
                try {
                    val appContext = it.applicationContext ?: it
                    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .remove(KEY_STRICT_ACTIVE)
                        .remove(KEY_STRICT_END_TIME)
                        .commit()
                } catch (_: Exception) {}
            }
        }

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, StayFocusedDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) ?: false
        }

        fun createAddDeviceAdminIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Monk Mode requires Device Administrator to prevent uninstallation and settings tampering while Strict Mode is active."
                )
            }
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for Stay Focused.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        Log.w(TAG, "Device Admin disable requested.")
        val isStrict = isStrictActive(context)
        return getDisableWarning(antiTamperEnabled = isStrict)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for Stay Focused.")
    }

    fun getDisableWarning(antiTamperEnabled: Boolean): CharSequence? {
        return if (antiTamperEnabled) {
            "Monk Mode (Stay Focused) Strict Mode protection is active. Deactivating Device Admin is locked to prevent uninstallation until your session expires."
        } else {
            null
        }
    }
}
