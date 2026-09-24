package com.stayfocused.app.notification

/**
 * Pure Kotlin data model representing an incoming notification payload.
 * Completely decoupled from Android Framework classes (`StatusBarNotification`)
 * for fast, deterministic JVM unit testing.
 */
data class NotificationInfo(
    val packageName: String,
    val isOngoing: Boolean,
    val category: String?,
    val channelId: String?
)

enum class NotificationInterceptionResult(val shouldCancel: Boolean) {
    ALLOW_NOT_BLOCKED(shouldCancel = false),
    ALLOW_SAFETY_CRITICAL(shouldCancel = false),
    ALLOW_BREAK_ACTIVE(shouldCancel = false),
    SUPPRESS_DISTRACTION(shouldCancel = true)
}

/**
 * Pure Kotlin decision engine for notification interception.
 * Enforces strict safety-first policies:
 * 1. Ongoing notifications (playback, active ongoing calls, navigation) are NEVER canceled.
 * 2. System UI, telephony, and telecom notifications are NEVER canceled.
 * 3. Alarm, call, and emergency categories are NEVER canceled.
 * 4. Active breaks bypass notification suppression.
 */
class NotificationDecisionEngine(
    private val systemPackageAllowlist: Set<String> = DEFAULT_SYSTEM_ALLOWLIST
) {

    fun shouldSuppressNotification(
        notification: NotificationInfo,
        isPackageBlocked: Boolean,
        isBreakActive: Boolean
    ): NotificationInterceptionResult {
        // 1. Safety Exception: Never cancel ongoing notifications (music, ongoing calls, turn-by-turn)
        if (notification.isOngoing) {
            return NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL
        }

        // 2. Safety Exception: Never cancel critical categories (calls, alarms, system alerts)
        val normalizedCategory = notification.category?.lowercase()
        if (normalizedCategory in CRITICAL_CATEGORIES) {
            return NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL
        }

        // 3. Safety Exception: Essential system and dialer packages
        if (notification.packageName in systemPackageAllowlist) {
            return NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL
        }

        // 4. Temporary break active: User is taking an authorized break
        if (isBreakActive) {
            return NotificationInterceptionResult.ALLOW_BREAK_ACTIVE
        }

        // 5. Check if the app is currently in a blocked state
        return if (isPackageBlocked) {
            NotificationInterceptionResult.SUPPRESS_DISTRACTION
        } else {
            NotificationInterceptionResult.ALLOW_NOT_BLOCKED
        }
    }

    companion object {
        val CRITICAL_CATEGORIES = setOf(
            "call",
            "alarm",
            "err",
            "reminder",
            "event"
        )

        val DEFAULT_SYSTEM_ALLOWLIST = setOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.google.android.dialer",
            "com.android.server.telecom",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "com.stayfocused.app"
        )
    }
}
