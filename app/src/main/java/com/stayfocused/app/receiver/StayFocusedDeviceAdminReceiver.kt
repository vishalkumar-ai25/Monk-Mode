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

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, StayFocusedDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) ?: false
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for Stay Focused.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        Log.w(TAG, "Device Admin disable requested.")
        return getDisableWarning(antiTamperEnabled = BuildConfig.ANTI_TAMPER_ENABLED)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled for Stay Focused.")
    }

    fun getDisableWarning(antiTamperEnabled: Boolean): CharSequence? {
        return if (antiTamperEnabled) {
            "Stay Focused Strict Mode protection is active. Deactivating Device Admin is restricted to prevent impulsive uninstallation. Use your emergency recovery code to deactivate."
        } else {
            null
        }
    }
}
