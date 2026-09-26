package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FailsafeEventType
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FailsafeLogFormatterTest {

    private val formatter = FailsafeLogFormatter(ZoneId.of("UTC"))

    @Test
    fun getEventDisplayName_mapsKnownEventTypesCorrectly() {
        assertEquals(
            "Delayed Unlock Requested",
            formatter.getEventDisplayName(FailsafeEventType.DELAY_REQUESTED.name)
        )
        assertEquals(
            "Delayed Unlock Cancelled",
            formatter.getEventDisplayName(FailsafeEventType.DELAY_CANCELLED.name)
        )
        assertEquals(
            "Delayed Unlock Finalized",
            formatter.getEventDisplayName(FailsafeEventType.DELAY_FINALIZED.name)
        )
        assertEquals(
            "Emergency Recovery Code",
            formatter.getEventDisplayName(FailsafeEventType.RECOVERY_CODE_ENTERED.name)
        )
        assertEquals(
            "Boot Grace Window Used",
            formatter.getEventDisplayName(FailsafeEventType.BOOT_GRACE_WINDOW_USED.name)
        )
        assertEquals(
            "UNKNOWN_EVENT",
            formatter.getEventDisplayName("UNKNOWN_EVENT")
        )
    }

    @Test
    fun formatTimestamp_producesStandardReadableDate() {
        // 2026-09-26T12:00:00Z = 1790424000000L
        val instant = Instant.parse("2026-09-26T12:00:00Z")
        val formatted = formatter.formatTimestamp(instant.toEpochMilli())

        assertTrue(formatted.contains("Sep 26, 2026"))
        assertTrue(formatted.contains("12:00"))
    }

    @Test
    fun getStatusLabel_returnsAppropriateIndicator() {
        val successLog = FailsafeLogEntity(
            eventType = FailsafeEventType.DELAY_REQUESTED.name,
            details = "Requested 24h delay",
            success = true
        )
        val failureLog = FailsafeLogEntity(
            eventType = FailsafeEventType.RECOVERY_CODE_ENTERED.name,
            details = "Invalid code attempt",
            success = false
        )

        assertEquals("GRANTED", formatter.getStatusLabel(successLog))
        assertEquals("REJECTED", formatter.getStatusLabel(failureLog))
    }
}
