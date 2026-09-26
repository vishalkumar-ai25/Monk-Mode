package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FailsafeEventType
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure Kotlin presentation formatter for Failsafe Integrity Log entries.
 */
class FailsafeLogFormatter(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm", Locale.US)
        .withZone(zoneId)

    fun formatTimestamp(epochMs: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(epochMs)
            dateFormatter.format(instant)
        } catch (e: Exception) {
            epochMs.toString()
        }
    }

    fun getEventDisplayName(eventType: String): String {
        return when (eventType) {
            FailsafeEventType.DELAY_REQUESTED.name -> "Delayed Unlock Requested"
            FailsafeEventType.DELAY_CANCELLED.name -> "Delayed Unlock Cancelled"
            FailsafeEventType.DELAY_FINALIZED.name -> "Delayed Unlock Finalized"
            FailsafeEventType.RECOVERY_CODE_ENTERED.name -> "Emergency Recovery Code"
            FailsafeEventType.BOOT_GRACE_WINDOW_USED.name -> "Boot Grace Window Used"
            else -> eventType
        }
    }

    fun getStatusLabel(log: FailsafeLogEntity): String {
        return if (log.success) "GRANTED" else "REJECTED"
    }
}
