package com.stayfocused.app.domain

import com.stayfocused.app.tracker.UsageEventRecord
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Event type constants aligned with Android's android.app.usage.UsageEvents.Event.
 */
object UsageEventTypes {
    const val ACTIVITY_RESUMED = 1
    const val ACTIVITY_PAUSED = 2
    const val SCREEN_INTERACTIVE = 15
    const val SCREEN_NON_INTERACTIVE = 16
    const val KEYGUARD_SHOWN = 17
    const val KEYGUARD_HIDDEN = 18
    const val DEVICE_SHUTDOWN = 26
    const val DEVICE_STARTUP = 27
}

data class HourlyAppSlice(
    val epochDay: Long,
    val hourOfDay: Int,
    val packageName: String,
    val durationMs: Long
)

data class HourlyDeviceMetrics(
    val epochDay: Long,
    val hourOfDay: Int,
    val pickupCount: Int,
    val totalScreenTimeMs: Long
)

data class ReconstructedTelemetry(
    val appSlices: List<HourlyAppSlice>,
    val deviceMetrics: List<HourlyDeviceMetrics>
)

/**
 * Pure Kotlin reconstructor for Android raw UsageEvents stream.
 * 
 * Guarantees:
 * 1. Zero Android framework dependencies (100% JVM unit testable).
 * 2. Mathematical hour-boundary session slicing across midnight, DST, and sub-hour timezones (e.g. IST UTC+05:30).
 * 3. Ghost usage protection: sessions are strictly bounded by screen-off events and unclosed sessions are capped at 2 hours.
 * 4. False-positive pickup immunity: calls, alarms, and lock screen glances do not trigger pickups on secure devices.
 */
class UsageEventsReconstructor(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val isKeyguardSecure: Boolean = true
) {
    companion object {
        const val MAX_UNCLOSED_SESSION_MS = 2 * 3600 * 1000L // 2-hour safety clamp for unclosed sessions (crashes/LMK without pause)
        private const val INSECURE_WAKE_TIMEOUT_MS = 10_000L
        private const val INSECURE_PICKUP_DEBOUNCE_MS = 30_000L
    }

    private enum class LockState { LOCKED, UNLOCKED }

    /**
     * Slices a single continuous foreground session [sessionStart, sessionEnd] for [packageName]
     * across local clock hour boundaries using [zoneId].
     */
    fun sliceSessionAcrossHours(
        packageName: String,
        sessionStart: Long,
        sessionEnd: Long
    ): List<HourlyAppSlice> {
        if (packageName.isBlank() || sessionEnd <= sessionStart) return emptyList()

        val lowerPkg = packageName.lowercase().trim()
        val slices = mutableListOf<HourlyAppSlice>()

        var currentZdt = Instant.ofEpochMilli(sessionStart).atZone(zoneId)
        val startOfHourZdt = currentZdt.truncatedTo(ChronoUnit.HOURS)
        var currentHourStartMs = startOfHourZdt.toInstant().toEpochMilli()
        var nextHourZdt = startOfHourZdt.plusHours(1)
        var currentHourEndMs = nextHourZdt.toInstant().toEpochMilli()

        while (currentHourStartMs < sessionEnd) {
            val sliceStart = maxOf(sessionStart, currentHourStartMs)
            val sliceEnd = minOf(sessionEnd, currentHourEndMs)
            val duration = sliceEnd - sliceStart

            if (duration > 0) {
                val epochDay = currentZdt.toLocalDate().toEpochDay()
                val hourOfDay = currentZdt.hour
                slices.add(
                    HourlyAppSlice(
                        epochDay = epochDay,
                        hourOfDay = hourOfDay,
                        packageName = lowerPkg,
                        durationMs = duration
                    )
                )
            }

            currentZdt = nextHourZdt
            currentHourStartMs = currentHourEndMs
            nextHourZdt = currentZdt.plusHours(1)
            currentHourEndMs = nextHourZdt.toInstant().toEpochMilli()
        }

        return slices
    }

    /**
     * Reconstructs all hourly application usage slices and device-level pickup metrics
     * from a chronological stream of [UsageEventRecord]s bounded by [windowStart, windowEnd].
     */
    fun reconstruct(
        events: Sequence<UsageEventRecord>,
        windowStart: Long,
        windowEnd: Long
    ): ReconstructedTelemetry {
        if (windowEnd <= windowStart) {
            return ReconstructedTelemetry(emptyList(), emptyList())
        }

        val appSlicesAccumulator = mutableMapOf<Triple<Long, Int, String>, Long>()
        val devicePickupsAccumulator = mutableMapOf<Pair<Long, Int>, Int>()

        var currentActivePkg: String? = null
        var sessionStart: Long = 0L
        var activeActivityCount: Int = 0
        var isScreenInteractive: Boolean = true

        var lockState = LockState.LOCKED
        var pendingInsecureWakeTimestamp: Long = 0L
        var lastInsecurePickupTimestamp: Long = 0L

        fun creditCurrentSession(timestamp: Long) {
            val pkg = currentActivePkg ?: return
            if (sessionStart <= 0L) return

            val cappedTimestamp = minOf(timestamp, sessionStart + MAX_UNCLOSED_SESSION_MS)
            val effectiveStart = maxOf(sessionStart, windowStart)
            val effectiveEnd = minOf(cappedTimestamp, windowEnd)

            if (effectiveEnd > effectiveStart) {
                val slices = sliceSessionAcrossHours(pkg, effectiveStart, effectiveEnd)
                for (slice in slices) {
                    val key = Triple(slice.epochDay, slice.hourOfDay, slice.packageName)
                    appSlicesAccumulator[key] = (appSlicesAccumulator[key] ?: 0L) + slice.durationMs
                }
            }

            currentActivePkg = null
            sessionStart = 0L
            activeActivityCount = 0
        }

        fun recordPickup(timestamp: Long) {
            if (timestamp < windowStart || timestamp > windowEnd) return
            val zdt = Instant.ofEpochMilli(timestamp).atZone(zoneId)
            val key = Pair(zdt.toLocalDate().toEpochDay(), zdt.hour)
            devicePickupsAccumulator[key] = (devicePickupsAccumulator[key] ?: 0) + 1
        }

        for (event in events) {
            when (event.eventType) {
                UsageEventTypes.ACTIVITY_RESUMED -> {
                    val newPkg = event.packageName?.takeIf { it.isNotBlank() } ?: continue

                    // Insecure device pickup confirmation
                    if (!isKeyguardSecure && pendingInsecureWakeTimestamp > 0L) {
                        if (event.timestamp - pendingInsecureWakeTimestamp <= INSECURE_WAKE_TIMEOUT_MS) {
                            if (event.timestamp - lastInsecurePickupTimestamp >= INSECURE_PICKUP_DEBOUNCE_MS) {
                                recordPickup(pendingInsecureWakeTimestamp)
                                lastInsecurePickupTimestamp = event.timestamp
                            }
                        }
                        pendingInsecureWakeTimestamp = 0L
                    }

                    if (currentActivePkg != null) {
                        if (newPkg.equals(currentActivePkg, ignoreCase = true)) {
                            activeActivityCount++
                            continue
                        }
                        // Package switch: close previous
                        creditCurrentSession(event.timestamp)
                    }

                    if (isScreenInteractive) {
                        currentActivePkg = newPkg
                        sessionStart = event.timestamp
                        activeActivityCount = 1
                    }
                }

                UsageEventTypes.ACTIVITY_PAUSED -> {
                    val pausedPkg = event.packageName
                    if (currentActivePkg != null && (pausedPkg == null || pausedPkg.equals(currentActivePkg, ignoreCase = true))) {
                        activeActivityCount--
                        if (activeActivityCount <= 0) {
                            creditCurrentSession(event.timestamp)
                        }
                    }
                }

                UsageEventTypes.KEYGUARD_HIDDEN -> {
                    if (isKeyguardSecure && lockState == LockState.LOCKED) {
                        lockState = LockState.UNLOCKED
                        recordPickup(event.timestamp)
                    }
                }

                UsageEventTypes.SCREEN_INTERACTIVE -> {
                    isScreenInteractive = true
                    if (!isKeyguardSecure) {
                        pendingInsecureWakeTimestamp = event.timestamp
                    }
                }

                UsageEventTypes.SCREEN_NON_INTERACTIVE,
                UsageEventTypes.KEYGUARD_SHOWN,
                UsageEventTypes.DEVICE_SHUTDOWN -> {
                    isScreenInteractive = false
                    lockState = LockState.LOCKED
                    pendingInsecureWakeTimestamp = 0L
                    if (currentActivePkg != null) {
                        creditCurrentSession(event.timestamp)
                    }
                }
            }
        }

        // Flush any trailing active session up to windowEnd
        if (currentActivePkg != null && sessionStart > 0L && isScreenInteractive) {
            creditCurrentSession(windowEnd)
        }

        // Aggregate results
        val appSlices = appSlicesAccumulator.map { (key, durationMs) ->
            HourlyAppSlice(
                epochDay = key.first,
                hourOfDay = key.second,
                packageName = key.third,
                durationMs = durationMs
            )
        }.sortedWith(compareBy({ it.epochDay }, { it.hourOfDay }, { -it.durationMs }))

        // Compute device metrics by summing app screen times per (epochDay, hourOfDay)
        val deviceHours = (devicePickupsAccumulator.keys + appSlices.map { Pair(it.epochDay, it.hourOfDay) }).distinct()
        val deviceMetrics = deviceHours.map { hourKey ->
            val totalScreenTimeMs = appSlices
                .filter { it.epochDay == hourKey.first && it.hourOfDay == hourKey.second }
                .sumOf { it.durationMs }
            val pickups = devicePickupsAccumulator[hourKey] ?: 0
            HourlyDeviceMetrics(
                epochDay = hourKey.first,
                hourOfDay = hourKey.second,
                pickupCount = pickups,
                totalScreenTimeMs = totalScreenTimeMs
            )
        }.sortedWith(compareBy({ it.epochDay }, { it.hourOfDay }))

        return ReconstructedTelemetry(appSlices, deviceMetrics)
    }
}
