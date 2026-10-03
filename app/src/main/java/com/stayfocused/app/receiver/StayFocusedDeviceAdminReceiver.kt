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
        val isStrictActive = try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val db = com.stayfocused.app.data.local.StayFocusedDatabase.getInstance(context)
                val session = db.strictSessionDao().getActiveStrictSessionSync()
                session != null && session.isActive && System.currentTimeMillis() < session.targetEndTime
            }
        } catch (e: Exception) {
            false
        }
        return getDisableWarning(antiTamperEnabled = isStrictActive)
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
