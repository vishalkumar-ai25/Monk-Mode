package com.stayfocused.app.strict

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrictModeManagerTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase
    private lateinit var preferences: StrictPreferences
    private lateinit var failsafeManager: FailsafeManager
    private lateinit var strictManager: StrictModeManager
    private var simulatedTime: Long = 1_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = StrictPreferences(context)
        failsafeManager = FailsafeManager(
            recoveryCodeDao = db.recoveryCodeDao(),
            strictSessionDao = db.strictSessionDao(),
            failsafeLogDao = db.failsafeLogDao(),
            timeProvider = { simulatedTime }
        )
        strictManager = StrictModeManager(
            database = db,
            preferences = preferences,
            failsafeManager = failsafeManager,
            timeProvider = { simulatedTime }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testStartStrictSessionHardcore() = runBlocking {
        val started = strictManager.startStrictSession(
            durationMinutes = 60,
            unlockMethod = StrictUnlockMethod.HARDCORE
        )
        assertTrue(started)

        val active = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull(active)
        assertTrue(active!!.isActive)
        assertEquals(simulatedTime + 60 * 60 * 1000L, active.targetEndTime)
        assertEquals(StrictUnlockMethod.HARDCORE, preferences.unlockMethod)

        val state = strictManager.evaluateState(active)
        assertTrue(state.isActive)
        assertEquals(60 * 60 * 1000L, state.remainingMillis)
        assertEquals(StrictUnlockMethod.HARDCORE, state.unlockMethod)
    }

    @Test
    fun testStartStrictSessionWithPinVerification() = runBlocking {
        val pin = "4321"
        strictManager.startStrictSession(
            durationMinutes = 30,
            unlockMethod = StrictUnlockMethod.PIN,
            pin = pin
        )

        val active = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull(active)

        // Wrong PIN fails
        val wrongPin = strictManager.verifyAndUnlockWithPin("0000")
        assertFalse(wrongPin)
        assertTrue(db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true)

        // Correct PIN succeeds
        val rightPin = strictManager.verifyAndUnlockWithPin("4321")
        assertTrue(rightPin)
        assertEquals(null, db.strictSessionDao().getActiveStrictSessionSync())
    }

    @Test
    fun testTypingFrictionChallengeVerification() = runBlocking {
        val phrase = "I choose focus over distraction"
        strictManager.startStrictSession(
            durationMinutes = 45,
            unlockMethod = StrictUnlockMethod.TYPING_PHRASE,
            typingPhrase = phrase
        )

        // Mismatched phrase fails
        val wrong = strictManager.verifyAndUnlockWithPhrase("I want to quit")
        assertFalse(wrong)
        assertTrue(db.strictSessionDao().getActiveStrictSessionSync()?.isActive == true)

        // Exact phrase succeeds
        val correct = strictManager.verifyAndUnlockWithPhrase(phrase)
        assertTrue(correct)
        assertEquals(null, db.strictSessionDao().getActiveStrictSessionSync())
    }

    @Test
    fun testSessionExpirationEvaluatesInactive() = runBlocking {
        strictManager.startStrictSession(
            durationMinutes = 15,
            unlockMethod = StrictUnlockMethod.HARDCORE
        )

        val active = db.strictSessionDao().getActiveStrictSessionSync()
        assertTrue(strictManager.isSessionActive(active))

        // Advance simulated time past targetEndTime
        simulatedTime += 16 * 60 * 1000L
        assertFalse(strictManager.isSessionActive(active))

        val state = strictManager.evaluateState(active)
        assertFalse(state.isActive)
        assertEquals(0L, state.remainingMillis)
    }

    @Test
    fun testEmergencyRecoveryCodeDeactivatesStrictSession() = runBlocking {
        strictManager.startStrictSession(
            durationMinutes = 120,
            unlockMethod = StrictUnlockMethod.HARDCORE
        )

        val recoveryCode = strictManager.generateRecoveryCode()
        assertNotNull(recoveryCode)

        val unlocked = strictManager.verifyAndConsumeRecoveryCode(recoveryCode)
        assertTrue(unlocked)
        assertEquals(null, db.strictSessionDao().getActiveStrictSessionSync())
    }
}
