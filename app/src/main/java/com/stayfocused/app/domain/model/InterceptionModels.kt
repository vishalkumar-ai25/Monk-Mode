package com.stayfocused.app.domain.model

import java.time.ZoneId

enum class LimitType {
    TIME_LIMIT,
    LAUNCH_LIMIT
}

sealed interface BlockReason {
    data class LimitReached(
        val appName: String,
        val limitType: LimitType,
        val used: Long,
        val limit: Long
    ) : BlockReason

    data class ProfileActive(
        val profileName: String,
        val profileId: Long
    ) : BlockReason

    data class SettingsTamper(
        val message: String
    ) : BlockReason

    data class ManuallyBlocked(
        val appName: String
    ) : BlockReason
}

sealed interface InterceptionResult {
    data class Allow(
        val reason: String? = null
    ) : InterceptionResult

    data class Block(
        val reason: BlockReason
    ) : InterceptionResult
}

data class AppLimitSnapshot(
    val packageName: String,
    val appName: String,
    val dailyTimeLimitMinutes: Int = 0,
    val dailyLaunchLimit: Int = 0,
    val currentDayUsageMs: Long = 0L,
    val currentDayLaunches: Int = 0,
    val isBlocked: Boolean = false
)

data class FocusProfileRule(
    val profileId: Long,
    val name: String,
    val isActive: Boolean = true,
    val isStrictMode: Boolean = false,
    val scheduleStartTime: String? = null,
    val scheduleEndTime: String? = null,
    val activeDaysMask: Int = 0, // Bit 0 = Mon, Bit 1 = Tue, ..., Bit 6 = Sun
    val blockedPackages: Set<String> = emptySet(),
    val blockedDomains: Set<String> = emptySet()
)

data class InterceptionContext(
    val targetPackageName: String,
    val currentTimeMillis: Long,
    val appLimit: AppLimitSnapshot? = null,
    val activeProfiles: List<FocusProfileRule> = emptyList(),
    val isStrictModeActive: Boolean = false,
    val antiTamperEnabled: Boolean = false,
    val isSettingsOrInstaller: Boolean = false,
    val isGracePeriodActive: Boolean = false,
    val zoneId: ZoneId = ZoneId.systemDefault()
)
