package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.FocusProfileRule
import com.stayfocused.app.domain.model.InterceptionContext
import com.stayfocused.app.domain.model.InterceptionResult
import com.stayfocused.app.domain.model.LimitType
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure Kotlin decision engine for real-time app interception.
 * Contains zero Android framework dependencies to ensure 100% JVM unit testability.
 */
class InterceptionDecisionEngine {

    companion object {
        const val SELF_PACKAGE = "com.stayfocused.app"
    }

    /**
     * Evaluates incoming package name against active limits, schedules, and strict mode policies.
     */
    fun evaluate(context: InterceptionContext): InterceptionResult {
        val target = context.targetPackageName.trim().lowercase()

        // Rule 0: Never block our own app
        if (target == SELF_PACKAGE) {
            return InterceptionResult.Allow("Self application")
        }

        // Rule 1: Anti-Tamper & Settings/Installer protection
        if (context.isSettingsOrInstaller) {
            return evaluateSettingsTamper(context)
        }

        // Rule 2: Active Focus Profiles (Manual or Scheduled)
        for (profile in context.activeProfiles) {
            if (isProfileActiveNow(profile, context.currentTimeMillis, context.zoneId)) {
                if (profile.blockedPackages.any { it.trim().lowercase() == target }) {
                    return InterceptionResult.Block(
                        BlockReason.ProfileActive(profile.name, profile.profileId)
                    )
                }
            }
        }

        // Rule 3: Manual App Block
        val limit = context.appLimit
        if (limit != null && limit.isBlocked) {
            return InterceptionResult.Block(BlockReason.ManuallyBlocked(limit.appName))
        }

        // Rule 4: Daily Screen Time Limit
        if (limit != null && limit.dailyTimeLimitMinutes > 0) {
            val limitMs = limit.dailyTimeLimitMinutes * 60 * 1000L
            if (limit.currentDayUsageMs >= limitMs) {
                return InterceptionResult.Block(
                    BlockReason.LimitReached(
                        appName = limit.appName,
                        limitType = LimitType.TIME_LIMIT,
                        used = limit.currentDayUsageMs,
                        limit = limitMs
                    )
                )
            }
        }

        // Rule 5: Daily Launch Limit
        if (limit != null && limit.dailyLaunchLimit > 0) {
            if (limit.currentDayLaunches >= limit.dailyLaunchLimit) {
                return InterceptionResult.Block(
                    BlockReason.LimitReached(
                        appName = limit.appName,
                        limitType = LimitType.LAUNCH_LIMIT,
                        used = limit.currentDayLaunches.toLong(),
                        limit = limit.dailyLaunchLimit.toLong()
                    )
                )
            }
        }

        // Default: App is allowed
        return InterceptionResult.Allow()
    }

    private fun evaluateSettingsTamper(context: InterceptionContext): InterceptionResult {
        // If anti-tamper is disabled (e.g. debug build variant), allow settings access
        if (!context.antiTamperEnabled) {
            return InterceptionResult.Allow("Anti-tamper disabled in debug variant")
        }

        // If boot grace period is active (e.g. 3-5 min after reboot), allow settings to fix crashes
        if (context.isGracePeriodActive) {
            return InterceptionResult.Allow("Boot grace period active")
        }

        // If strict mode is active, block settings/installer to prevent disabling or force-stopping
        if (context.isStrictModeActive) {
            return InterceptionResult.Block(
                BlockReason.SettingsTamper("Settings access is locked during active Strict Mode.")
            )
        }

        return InterceptionResult.Allow()
    }

    /**
     * Determines whether a focus profile rule is actively in effect at the given timestamp.
     */
    fun isProfileActiveNow(
        profile: FocusProfileRule,
        currentTimeMillis: Long,
        zoneId: ZoneId
    ): Boolean {
        if (!profile.isActive) return false

        // If no schedule is configured, manual active status applies 24/7
        val startTimeStr = profile.scheduleStartTime
        val endTimeStr = profile.scheduleEndTime
        if (startTimeStr.isNullOrBlank() || endTimeStr.isNullOrBlank() || profile.activeDaysMask == 0) {
            return true
        }

        val zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(currentTimeMillis), zoneId)
        val dayOfWeek = zonedDateTime.dayOfWeek // Monday = 1 ... Sunday = 7
        val dayBit = 1 shl (dayOfWeek.value - 1)

        val startTime = parseLocalTime(startTimeStr) ?: return true
        val endTime = parseLocalTime(endTimeStr) ?: return true
        val currentTime = zonedDateTime.toLocalTime()

        return if (!startTime.isAfter(endTime)) {
            // Same-day schedule (e.g. 09:00 to 17:00)
            val isDayActive = (profile.activeDaysMask and dayBit) != 0
            isDayActive && !currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)
        } else {
            // Overnight schedule spanning midnight (e.g. 22:00 to 06:00)
            if (!currentTime.isBefore(startTime)) {
                // Before midnight: applies if today is in activeDaysMask
                (profile.activeDaysMask and dayBit) != 0
            } else if (!currentTime.isAfter(endTime)) {
                // After midnight: applies if yesterday was in activeDaysMask
                val yesterdayBit = 1 shl ((dayOfWeek.value - 2 + 7) % 7)
                (profile.activeDaysMask and yesterdayBit) != 0
            } else {
                false
            }
        }
    }

    private fun parseLocalTime(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.trim().split(":")
            val hour = parts[0].toInt()
            val min = if (parts.size > 1) parts[1].toInt() else 0
            LocalTime.of(hour, min)
        } catch (e: Exception) {
            null
        }
    }
}
