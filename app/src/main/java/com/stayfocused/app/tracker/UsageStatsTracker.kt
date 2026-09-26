package com.stayfocused.app.tracker

import android.app.AppOpsManager
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
    private val usageStatsProvider: ((startTime: Long, endTime: Long) -> Map<String, Long>)? = null
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

        val manager = usageStatsManager ?: return emptyMap()

        // Modern Android (API 28+): queryAndAggregateUsageStats returns pre-aggregated totals
        return try {
            val aggregated = manager.queryAndAggregateUsageStats(startTime, endTime)
            val aggregatedResult = aggregated?.mapNotNull { (pkg, stats) ->
                if (stats.totalTimeInForeground > 0L) {
                    pkg.lowercase() to stats.totalTimeInForeground
                } else null
            }?.toMap() ?: emptyMap()

            if (aggregatedResult.isNotEmpty()) {
                aggregatedResult
            } else {
                // Fallback for devices/APIs where queryAndAggregateUsageStats returns an empty map
                val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                if (!statsList.isNullOrEmpty()) {
                    statsList.groupBy { it.packageName.lowercase() }
                        .mapValues { (_, list) -> list.maxOfOrNull { it.totalTimeInForeground } ?: 0L }
                        .filterValues { it > 0L }
                } else {
                    val bestList = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
                    bestList.orEmpty().groupBy { it.packageName.lowercase() }
                        .mapValues { (_, list) -> list.maxOfOrNull { it.totalTimeInForeground } ?: 0L }
                        .filterValues { it > 0L }
                }
            }
        } catch (e: Exception) {
            // Fallback for API 26-27 or system query failure
            try {
                val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
                statsList.groupBy { it.packageName.lowercase() }
                    .mapValues { (_, list) -> list.maxOfOrNull { it.totalTimeInForeground } ?: 0L }
                    .filterValues { it > 0L }
            } catch (e2: Exception) {
                emptyMap()
            }
        }
    }

    data class TopAppUsage(
        val packageName: String,
        val appName: String,
        val usageMs: Long
    )

    /**
     * Calculates the total device foreground usage in milliseconds across all apps (excluding Monk Mode itself).
     */
    fun getTotalDeviceUsageMs(
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): Long {
        val usageMap = queryForegroundUsage(startTime, endTime)
        val selfPkg = context.packageName.lowercase()
        return usageMap.filterKeys { it != selfPkg }.values.sum()
    }

    /**
     * Returns top used apps for today sorted descending by foreground usage duration.
     */
    fun getTopUsedApps(
        limit: Int = 5,
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): List<TopAppUsage> {
        val usageMap = queryForegroundUsage(startTime, endTime)
        val selfPkg = context.packageName.lowercase()
        val pm = context.packageManager

        return usageMap
            .filterKeys { it != selfPkg }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, usageMs) ->
                val appName = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    pkg.substringAfterLast('.').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                TopAppUsage(
                    packageName = pkg,
                    appName = appName,
                    usageMs = usageMs
                )
            }
    }

    /**
     * Synchronizes today's usage statistics into Room DB for all registered AppLimitEntity records.
     */
    suspend fun syncUsageWithDatabase(appLimitDao: AppLimitDao) {
        if (!hasUsageStatsPermission()) return

        val usageMap = queryForegroundUsage()
        val trackedLimits = appLimitDao.getAllAppLimitsSync()

        for (limit in trackedLimits) {
            val actualUsageMs = usageMap[limit.packageName.lowercase()] ?: 0L
            if (actualUsageMs != limit.currentDayUsageMs) {
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
        val updatedLaunches = existing.currentDayLaunches + 1
        appLimitDao.updateUsageAndLaunches(
            packageName = existing.packageName,
            usageMs = existing.currentDayUsageMs,
            launches = updatedLaunches
        )
    }

    companion object {
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
