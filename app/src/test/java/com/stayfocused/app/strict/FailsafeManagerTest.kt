package com.stayfocused.app.strict

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FailsafeManagerTest {

    private lateinit var db: StayFocusedDatabase
    private lateinit var failsafeManager: FailsafeManager
    private var simulatedTimeMs: Long = 1_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        failsafeManager = FailsafeManager(
            recoveryCodeDao = db.recoveryCodeDao(),
            strictSessionDao = db.strictSessionDao(),
            timeProvider = { simulatedTimeMs }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGenerateRecoveryCodeIs16CharsAndCannotBeRetrievedFromDatabase() = runBlocking {
        val plainCode = failsafeManager.generateAndStoreRecoveryCode()
        assertNotNull(plainCode)

        // 16 chars (or 4 chunks of 4 separated by hyphens)
        val normalized = plainCode.replace("-", "").trim()
        assertEquals("Normalized recovery code must be exactly 16 characters", 16, normalized.length)

        // Retrieve from database
        val storedEntity = db.recoveryCodeDao().getRecoveryCodeSync()
        assertNotNull(storedEntity)
        assertFalse(storedEntity!!.isConsumed)
        assertNotEquals("Plain text code must NEVER be stored in the database", plainCode, storedEntity.passwordHash)
        assertNotEquals("Plain text code must NEVER be stored in the database", normalized, storedEntity.passwordHash)
        assertTrue("Stored password hash must be non-empty", storedEntity.passwordHash.isNotEmpty())
        assertTrue("Stored salt must be non-empty", storedEntity.salt.isNotEmpty())
    }

    @Test
    fun testVerifyAndConsumeRecoveryCodeCancelsStrictSessions() = runBlocking {
        // Setup focus profile & active strict session
        val profileId = db.focusProfileDao().upsertProfile(
            FocusProfileEntity(id = 1, name = "Deep Work", isActive = true)
        )
        val sessionId = db.strictSessionDao().insertSession(
            StrictSessionEntity(
                profileId = profileId,
                startTime = simulatedTimeMs,
                targetEndTime = simulatedTimeMs + 4 * 3600 * 1000L,
                isActive = true
            )
        )
        assertTrue(db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true)

        val code = failsafeManager.generateAndStoreRecoveryCode()

        // Wrong code fails
        val wrongResult = failsafeManager.verifyAndConsumeRecoveryCode("WRONG-CODE-1234")
        assertFalse(wrongResult)
        assertTrue("Strict session must remain active on wrong code", db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true)

        // Correct code succeeds (even lowercase or with varying dashes)
        val lowerCode = code.lowercase()
        val correctResult = failsafeManager.verifyAndConsumeRecoveryCode(lowerCode)
        assertTrue(correctResult)

        // Strict session should be deactivated
        assertNull("All strict sessions must be canceled upon valid recovery code", db.strictSessionDao().getActiveStrictSessionSync())

        // Re-entering code must fail (already consumed)
        val replayResult = failsafeManager.verifyAndConsumeRecoveryCode(code)
        assertFalse("Consumed recovery code cannot be re-used", replayResult)
    }

    @Test
    fun testTimeDelayedUnlockEnforcesConfiguredDuration() = runBlocking {
        val profileId = db.focusProfileDao().upsertProfile(
            FocusProfileEntity(id = 2, name = "Study", isActive = true)
        )
        val delay24hMs = 24 * 60 * 60 * 1000L
        val sessionId = db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 10,
                profileId = profileId,
                startTime = simulatedTimeMs,
                targetEndTime = simulatedTimeMs + 48 * 3600 * 1000L,
                isActive = true
            )
        )

        // Request delayed unlock
        val requested = failsafeManager.requestDelayedUnlock(sessionId, delayDurationMs = delay24hMs)
        assertTrue(requested)

        val sessionAfterRequest = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull(sessionAfterRequest?.delayedUnlockRequestTime)
        assertEquals(simulatedTimeMs, sessionAfterRequest?.delayedUnlockRequestTime)

        // Check remaining duration immediately
        val remainingImmediately = failsafeManager.getDelayedUnlockRemainingMs(sessionAfterRequest!!)
        assertEquals(delay24hMs, remainingImmediately)

        // Advance time by 12 hours (halfway through delay)
        simulatedTimeMs += 12 * 60 * 60 * 1000L
        val remaining12h = failsafeManager.getDelayedUnlockRemainingMs(db.strictSessionDao().getActiveStrictSessionSync()!!)
        assertEquals(12 * 60 * 60 * 1000L, remaining12h)

        // Attempting to finalize early must fail
        val earlyUnlock = failsafeManager.tryFinalizeDelayedUnlock(sessionId)
        assertFalse("Cannot finalize unlock before delay duration elapses", earlyUnlock)
        assertTrue(db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true)

        // Advance time to 24h + 1ms
        simulatedTimeMs += 12 * 60 * 60 * 1000L + 1L
        val finalRemaining = failsafeManager.getDelayedUnlockRemainingMs(db.strictSessionDao().getActiveStrictSessionSync()!!)
        assertEquals(0L, finalRemaining)

        // Attempting to finalize now must succeed
        val finalUnlock = failsafeManager.tryFinalizeDelayedUnlock(sessionId)
        assertTrue("Unlock must succeed once full delay has elapsed", finalUnlock)
        assertNull("Session must be deactivated", db.strictSessionDao().getActiveStrictSessionSync())
    }

    @Test
    fun testCancelDelayedUnlockResetsRequest() = runBlocking {
        val profileId = db.focusProfileDao().upsertProfile(
            FocusProfileEntity(id = 3, name = "Reading", isActive = true)
        )
        val sessionId = db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 20,
                profileId = profileId,
                startTime = simulatedTimeMs,
                targetEndTime = simulatedTimeMs + 10 * 3600 * 1000L,
                isActive = true
            )
        )

        failsafeManager.requestDelayedUnlock(sessionId)
        assertNotNull(db.strictSessionDao().getActiveStrictSessionSync()?.delayedUnlockRequestTime)

        val cancelled = failsafeManager.cancelDelayedUnlock(sessionId)
        assertTrue(cancelled)

        val sessionAfterCancel = db.strictSessionDao().getActiveStrictSessionSync()
        assertNull("Request time must be reset to null after cancellation", sessionAfterCancel?.delayedUnlockRequestTime)
    }
}
