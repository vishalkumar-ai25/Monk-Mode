package com.stayfocused.app.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.stayfocused.app.data.local.dao.AppLimitDao
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * High-performance tracker wrapping Android's UsageStatsManager.
 * Aggregates cumulative foreground app usage from midnight (00:00:00) to now
 * and maintains package launch counts in Room.
 */
class UsageStatsTracker(
    private val context: Context,
    private val usageStatsManager: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager,
    private val appOpsChecker: (Context) -> Boolean = { ctx ->
        checkUsageStatsPermission(ctx)
    },
    private val usageStatsProvider: ((startTime: Long, endTime: Long) -> Map<String, Long>)? = null,
    private val eventStreamProvider: ((startTime: Long, endTime: Long) -> Sequence<UsageEventRecord>)? = null
) {

    /**
     * Defensively verifies whether the app has been granted PACKAGE_USAGE_STATS permission via AppOps.
     */
    fun hasUsageStatsPermission(): Boolean {
        return appOpsChecker(context)
    }

    /**
     * Calculates the epoch millisecond representing today's start at 00:00:00 in the given ZoneId.
     */
    fun getStartOfToday(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        return zdt.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Queries cumulative foreground time in milliseconds for each package from [startTime] to [endTime].
     * Returns a map of lowercase package names to foreground duration in milliseconds.
     */
    fun queryForegroundUsage(
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): Map<String, Long> {
        if (!hasUsageStatsPermission()) {
            return emptyMap()
        }

        if (usageStatsProvider != null) {
            return usageStatsProvider.invoke(startTime, endTime)
        }

        if (eventStreamProvider != null) {
            val events = eventStreamProvider.invoke(startTime, endTime)
            val result = processEventStream(events, startTime, endTime)
            if (result.isNotEmpty()) {
                return result
            }
        }

        val manager = usageStatsManager ?: return emptyMap()

        // Tier 1: Canonical UsageEvents reconstruction (exact millisecond precision, respects midnight)
        try {
            val eventUsage = queryUsageFromEvents(manager, startTime, endTime)
            if (eventUsage.isNotEmpty()) {
                return eventUsage
            }
        } catch (_: Exception) {}

        // Tier 2: Pre-aggregated system totals (may spill over on OEMs like ColorOS/Realme/OnePlus)
        try {
            val aggregated = manager.queryAndAggregateUsageStats(startTime, endTime)
            val result = aggregated.mapNotNull { (pkg, stats) ->
                if (stats.totalTimeInForeground > 0L) {
                    pkg.lowercase() to stats.totalTimeInForeground
                } else null
            }.toMap()
            if (result.isNotEmpty()) {
                return result
            }
        } catch (_: Exception) {}

        // Tier 3: Fallback using INTERVAL_DAILY
        try {
            val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime) ?: emptyList()
            val result = statsList.filter { it.totalTimeInForeground > 0L }
                .groupBy { it.packageName.lowercase() }
                .mapValues { (_, list) -> list.maxOf { it.totalTimeInForeground } }
            if (result.isNotEmpty()) {
                return result
            }
        } catch (_: Exception) {}

        // Tier 4: Fallback using finest available interval (INTERVAL_BEST)
        return try {
            val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime) ?: emptyList()
            statsList.filter { it.totalTimeInForeground > 0L }
                .groupBy { it.packageName.lowercase() }
                .mapValues { (_, list) -> list.maxOf { it.totalTimeInForeground } }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * Reconstructs exact foreground app usage from Android's raw UsageEvents stream.
     * Looks back up to 12 hours before [windowStart] to capture any session that may have
     * crossed the midnight boundary, then clamps accumulated usage strictly within [windowStart, windowEnd].
     */
    fun queryUsageFromEvents(
        manager: UsageStatsManager,
        windowStart: Long,
        windowEnd: Long
    ): Map<String, Long> {
        val searchStart = maxOf(0L, windowStart - 12 * 3600 * 1000L)
        val usageEvents = try {
            manager.queryEvents(searchStart, windowEnd)
        } catch (e: Exception) {
            return emptyMap()
        } ?: return emptyMap()

        val eventList = mutableListOf<UsageEventRecord>()
        val outEvent = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(outEvent)
            eventList.add(
                UsageEventRecord(
                    packageName = outEvent.packageName,
                    className = outEvent.className,
                    eventType = outEvent.eventType,
                    timestamp = outEvent.timeStamp
                )
            )
        }

        if (eventList.isEmpty()) {
            return emptyMap()
        }

        return processEventStream(eventList.asSequence(), windowStart, windowEnd)
    }

    /**
     * Processes a chronological sequence of usage events and aggregates exact foreground time
     * for each package strictly clamped to [windowStart, windowEnd].
     */
    fun processEventStream(
        events: Sequence<UsageEventRecord>,
        windowStart: Long,
        windowEnd: Long
    ): Map<String, Long> {
        if (windowEnd <= windowStart) return emptyMap()

        val usageMap = mutableMapOf<String, Long>()
        var currentActivePkg: String? = null
        var currentClassName: String? = null
        var sessionStart: Long = 0L

        fun creditCurrentSession(sessionEnd: Long) {
            val pkg = currentActivePkg ?: return
            if (sessionStart <= 0L) return

            if (sessionEnd > sessionStart) {
                val cappedEnd = minOf(sessionEnd, sessionStart + MAX_SINGLE_SESSION_MS)
                val effectiveStart = maxOf(sessionStart, windowStart)
                val effectiveEnd = minOf(cappedEnd, windowEnd)

                if (effectiveEnd > effectiveStart) {
                    val duration = effectiveEnd - effectiveStart
                    val lowerPkg = pkg.lowercase()
                    usageMap[lowerPkg] = (usageMap[lowerPkg] ?: 0L) + duration
                }
            }
            currentActivePkg = null
            currentClassName = null
            sessionStart = 0L
        }

        for (event in events) {
            val eventPkg = event.packageName
            val eventCls = event.className
            val eventTime = event.timestamp

            when (event.eventType) {
                EVENT_ACTIVITY_RESUMED -> {
                    if (currentActivePkg != null) {
                        creditCurrentSession(eventTime)
                    }
                    if (!eventPkg.isNullOrBlank()) {
                        currentActivePkg = eventPkg
                        currentClassName = eventCls
                        sessionStart = eventTime
                    }
                }

                EVENT_ACTIVITY_PAUSED -> {
                    if (currentActivePkg != null) {
                        val isSameClass = eventCls.isNullOrBlank() ||
                                currentClassName.isNullOrBlank() ||
                                eventCls.equals(currentClassName, ignoreCase = true)

                        if (isSameClass && (eventPkg.isNullOrBlank() || eventPkg.equals(currentActivePkg, ignoreCase = true))) {
                            creditCurrentSession(eventTime)
                        }
                    }
                }

                EVENT_SCREEN_NON_INTERACTIVE,
                EVENT_KEYGUARD_SHOWN,
                EVENT_DEVICE_SHUTDOWN -> {
                    if (currentActivePkg != null) {
                        creditCurrentSession(eventTime)
                    }
                }

                EVENT_SCREEN_INTERACTIVE,
                EVENT_DEVICE_STARTUP -> {
                    if (currentActivePkg != null) {
                        creditCurrentSession(eventTime)
                    }
                }
            }
        }

        if (currentActivePkg != null && sessionStart > 0L) {
            creditCurrentSession(windowEnd)
        }

        return usageMap.filterValues { it > 0L }
    }

    /**
     * Calculates the aggregate foreground screen time across all packages for the given window.
     */
    fun queryTotalDeviceScreenTimeMs(
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): Long {
        return queryForegroundUsage(startTime, endTime).values.sum()
    }

    /**
     * Queries the top used applications ordered by foreground time descending.
     */
    fun queryTopUsedApps(
        limit: Int = 10,
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): List<AppUsageInfo> {
        val usageMap = queryForegroundUsage(startTime, endTime)
        if (usageMap.isEmpty()) return emptyList()

        val pm = context.packageManager
        return usageMap.entries
            .asSequence()
            .filter { it.value > 0L }
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, usageMs) ->
                val label = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
                AppUsageInfo(
                    packageName = pkg,
                    appName = label,
                    foregroundTimeMs = usageMs
                )
            }
            .toList()
    }

    /**
     * Queries today's foreground duration in milliseconds for a single package.
     */
    fun queryPackageUsageToday(
        packageName: String,
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): Long {
        return queryForegroundUsage(startTime, endTime)[packageName.lowercase()] ?: 0L
    }

    /**
     * Synchronizes today's usage statistics into Room DB for all registered AppLimitEntity records.
     * Includes automatic 12:00 AM midnight reset catch-up if exact alarm was suppressed by OEM.
     */
    suspend fun syncUsageWithDatabase(appLimitDao: AppLimitDao) {
        if (!hasUsageStatsPermission()) return

        val startOfToday = getStartOfToday()
        val usageMap = queryForegroundUsage(startTime = startOfToday)
        val trackedLimits = appLimitDao.getAllAppLimitsSync()

        for (limit in trackedLimits) {
            val actualUsageMs = usageMap[limit.packageName.lowercase()] ?: 0L
            val isNewDay = limit.lastResetTimestamp < startOfToday

            if (isNewDay) {
                // Opportunistic 12:00 AM reset catchup
                appLimitDao.updateUsageLaunchesAndReset(
                    packageName = limit.packageName,
                    usageMs = actualUsageMs,
                    launches = 0,
                    resetTimestamp = System.currentTimeMillis()
                )
            } else if (actualUsageMs != limit.currentDayUsageMs) {
                appLimitDao.updateUsageAndLaunches(
                    packageName = limit.packageName,
                    usageMs = actualUsageMs,
                    launches = limit.currentDayLaunches
                )
            }
        }
    }

    /**
     * Increments the launch count for the given package if it is tracked in Room DB.
     */
    suspend fun recordAppLaunch(packageName: String, appLimitDao: AppLimitDao) {
        val existing = appLimitDao.getAppLimitSync(packageName) ?: return
        val startOfToday = getStartOfToday()
        val isNewDay = existing.lastResetTimestamp < startOfToday
        val updatedLaunches = if (isNewDay) 1 else existing.currentDayLaunches + 1

        if (isNewDay) {
            appLimitDao.updateUsageLaunchesAndReset(
                packageName = existing.packageName,
                usageMs = queryPackageUsageToday(packageName),
                launches = updatedLaunches,
                resetTimestamp = System.currentTimeMillis()
            )
        } else {
            appLimitDao.updateUsageAndLaunches(
                packageName = existing.packageName,
                usageMs = existing.currentDayUsageMs,
                launches = updatedLaunches
            )
        }
    }

    companion object {
        const val EVENT_ACTIVITY_RESUMED = 1
        const val EVENT_ACTIVITY_PAUSED = 2
        const val EVENT_SCREEN_INTERACTIVE = 15
        const val EVENT_SCREEN_NON_INTERACTIVE = 16
        const val EVENT_KEYGUARD_SHOWN = 17
        const val EVENT_KEYGUARD_HIDDEN = 18
        const val EVENT_DEVICE_SHUTDOWN = 26
        const val EVENT_DEVICE_STARTUP = 27

        private const val MAX_SINGLE_SESSION_MS = 12 * 3600 * 1000L // 12 hours safety clamp

        fun checkUsageStatsPermission(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        }
    }
}

data class UsageEventRecord(
    val packageName: String?,
    val className: String? = null,
    val eventType: Int,
    val timestamp: Long
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long
)

