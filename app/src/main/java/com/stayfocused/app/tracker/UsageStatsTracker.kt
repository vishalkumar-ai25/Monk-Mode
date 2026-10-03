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

        // Tier 1: Modern Android queryAndAggregateUsageStats returns pre-aggregated totals
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

        // Tier 2: Fallback for devices where queryAndAggregateUsageStats returns empty (INTERVAL_DAILY)
        try {
            val statsList = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime) ?: emptyList()
            val result = statsList.filter { it.totalTimeInForeground > 0L }
                .groupBy { it.packageName.lowercase() }
                .mapValues { (_, list) -> list.maxOf { it.totalTimeInForeground } }
            if (result.isNotEmpty()) {
                return result
            }
        } catch (_: Exception) {}

        // Tier 3: Fallback using finest available interval (INTERVAL_BEST)
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

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long
)

