package com.stayfocused.app.domain.model

/**
 * The critical OS subsystems required for Stay Focused enforcement.
 */
enum class ProtectionCheckType {
    ACCESSIBILITY,
    VPN,
    BATTERY_OPTIMIZATION,
    WATCHDOG
}

/**
 * Health status of an individual subsystem check.
 */
enum class ProtectionCheckStatus {
    PASS,
    WARN,
    FAIL
}

/**
 * Overall aggregate health status of Stay Focused protection.
 */
enum class ProtectionOverallStatus {
    GREEN,
    AMBER,
    RED
}

/**
 * Detailed outcome of an individual protection check.
 */
data class ProtectionCheckResult(
    val type: ProtectionCheckType,
    val status: ProtectionCheckStatus,
    val title: String,
    val summary: String,
    val actionLabel: String? = null
)

/**
 * Aggregate snapshot of all protection checks at a given point in time.
 */
data class ProtectionStatusSnapshot(
    val overallStatus: ProtectionOverallStatus,
    val checks: List<ProtectionCheckResult>,
    val timestamp: Long
)
