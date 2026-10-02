package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Pure Kotlin decision engine for evaluating and scheduling recurring strict mode focus windows.
 *
 * Handles:
 * - 7-day bitmask representation (Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64)
 * - Same-day focus windows (e.g., 09:00 to 17:00)
 * - Cross-midnight focus windows (e.g., 22:00 to 06:00, including Sunday-to-Monday week wrap)
 * - Emergency disarm window dismissals (`dismissedUntilEpochMs`)
 * - Punctual alarm boundary calculation clamped to prevent infinite loop re-fires
 */
class StrictScheduleEngine {

    companion object {
        const val MIN_ALARM_DELAY_MS = 5_000L
    }

    /**
     * Checks if [dayOfWeek] (1=Monday .. 7=Sunday) is active in [daysMask].
     */
    fun isDayActive(daysMask: Int, dayOfWeek: Int): Boolean {
        if (dayOfWeek !in 1..7) return false
        val bit = 1 shl (dayOfWeek - 1)
        return (daysMask and bit) != 0
    }

    /**
     * Determines whether [schedule] is active at the given [zdt] and [epochMs].
     */
    fun isScheduleActiveAt(
        schedule: StrictScheduleEntity,
        zdt: ZonedDateTime,
        epochMs: Long
    ): Boolean {
        if (!schedule.isEnabled) return false
        if (schedule.dismissedUntilEpochMs > epochMs) return false
        if (schedule.startMinuteOfDay == schedule.endMinuteOfDay) return false

        val currentMinute = zdt.hour * 60 + zdt.minute
        val currentDay = zdt.dayOfWeek.value // 1 (Mon) .. 7 (Sun)
        val yesterday = if (currentDay == 1) 7 else currentDay - 1

        return if (schedule.startMinuteOfDay < schedule.endMinuteOfDay) {
            // Same-day window
            isDayActive(schedule.daysOfWeekMask, currentDay) &&
                currentMinute in schedule.startMinuteOfDay until schedule.endMinuteOfDay
        } else {
            // Cross-midnight window
            val inTonight = isDayActive(schedule.daysOfWeekMask, currentDay) &&
                currentMinute >= schedule.startMinuteOfDay
            val inLastNight = isDayActive(schedule.daysOfWeekMask, yesterday) &&
                currentMinute < schedule.endMinuteOfDay
            inTonight || inLastNight
        }
    }

    /**
     * Returns all enabled, non-dismissed schedules active at the current moment.
     */
    fun getActiveSchedules(
        schedules: List<StrictScheduleEntity>,
        zdt: ZonedDateTime,
        epochMs: Long
    ): List<StrictScheduleEntity> {
        return schedules.filter { isScheduleActiveAt(it, zdt, epochMs) }
    }

    /**
     * Returns true if at least one schedule is currently active.
     */
    fun isAnyScheduleActive(
        schedules: List<StrictScheduleEntity>,
        zdt: ZonedDateTime,
        epochMs: Long
    ): Boolean {
        return getActiveSchedules(schedules, zdt, epochMs).isNotEmpty()
    }

    /**
     * Resolves the strictest deactivation challenge across overlapping active schedules.
     * Hierarchy: EXPIRATION_ONLY > COOL_DOWN > RANDOM_TEXT
     */
    fun getStrictestChallenge(challenges: List<String>): String {
        return when {
            challenges.contains("EXPIRATION_ONLY") -> "EXPIRATION_ONLY"
            challenges.contains("COOL_DOWN") -> "COOL_DOWN"
            challenges.contains("RANDOM_TEXT") -> "RANDOM_TEXT"
            else -> "EXPIRATION_ONLY"
        }
    }

    /**
     * Calculates the exact end epoch timestamp for the currently active window of [schedule].
     * Assumes [schedule] is active at [nowZdt].
     */
    fun calculateCurrentWindowEndTime(
        schedule: StrictScheduleEntity,
        nowZdt: ZonedDateTime
    ): Long {
        val endHour = schedule.endMinuteOfDay / 60
        val endMinute = schedule.endMinuteOfDay % 60
        val endLocalTime = LocalTime.of(endHour, endMinute)

        return if (schedule.startMinuteOfDay < schedule.endMinuteOfDay) {
            // Same day end
            nowZdt.toLocalDate().atTime(endLocalTime).atZone(nowZdt.zone).toInstant().toEpochMilli()
        } else {
            // Cross-midnight
            val currentMinute = nowZdt.hour * 60 + nowZdt.minute
            if (currentMinute >= schedule.startMinuteOfDay) {
                // Started today evening, ends tomorrow morning
                nowZdt.toLocalDate().plusDays(1).atTime(endLocalTime).atZone(nowZdt.zone).toInstant().toEpochMilli()
            } else {
                // Started yesterday evening, ends today morning
                nowZdt.toLocalDate().atTime(endLocalTime).atZone(nowZdt.zone).toInstant().toEpochMilli()
            }
        }
    }

    /**
     * Projects forward across the upcoming 8 days to calculate the earliest boundary epoch (start or end)
     * across all enabled schedules. Clamped to [nowEpochMs] + 5,000ms to guarantee no infinite alarm loops.
     */
    fun calculateNextBoundaryEpochMs(
        schedules: List<StrictScheduleEntity>,
        nowZdt: ZonedDateTime,
        nowEpochMs: Long
    ): Long? {
        val enabledSchedules = schedules.filter { it.isEnabled }
        if (enabledSchedules.isEmpty()) return null

        var earliestBoundary: Long? = null

        for (dayOffset in -1L..7L) {
            val candidateDate = nowZdt.toLocalDate().plusDays(dayOffset)
            val candidateDayOfWeek = candidateDate.dayOfWeek.value

            for (schedule in enabledSchedules) {
                if (!isDayActive(schedule.daysOfWeekMask, candidateDayOfWeek)) continue

                val startHour = schedule.startMinuteOfDay / 60
                val startMinute = schedule.startMinuteOfDay % 60
                val startZdt = candidateDate.atTime(startHour, startMinute).atZone(nowZdt.zone)
                val startEpoch = startZdt.toInstant().toEpochMilli()

                if (startEpoch > nowEpochMs) {
                    earliestBoundary = minOf(earliestBoundary ?: Long.MAX_VALUE, startEpoch)
                }

                val endHour = schedule.endMinuteOfDay / 60
                val endMinute = schedule.endMinuteOfDay % 60
                val endZdt = if (schedule.startMinuteOfDay < schedule.endMinuteOfDay) {
                    candidateDate.atTime(endHour, endMinute).atZone(nowZdt.zone)
                } else {
                    candidateDate.plusDays(1).atTime(endHour, endMinute).atZone(nowZdt.zone)
                }
                val endEpoch = endZdt.toInstant().toEpochMilli()

                if (endEpoch > nowEpochMs) {
                    earliestBoundary = minOf(earliestBoundary ?: Long.MAX_VALUE, endEpoch)
                }
            }
        }

        return earliestBoundary?.let { maxOf(nowEpochMs + MIN_ALARM_DELAY_MS, it) }
    }
}
