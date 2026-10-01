package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileSwitchDecisionEngineTest {

    private lateinit var engine: ProfileSwitchDecisionEngine

    private val profileWork = FocusProfileEntity(id = 1, name = "Work", isActive = true, isStrictMode = false)
    private val profilePersonal = FocusProfileEntity(id = 2, name = "Personal", isActive = false, isStrictMode = false)
    private val profileStrict = FocusProfileEntity(id = 3, name = "Deep Work", isActive = true, isStrictMode = true)

    @Before
    fun setUp() {
        engine = ProfileSwitchDecisionEngine()
    }

    @Test
    fun testAlreadyActiveProfileReturnsAlreadyActive() {
        val result = engine.evaluateSwitch(
            currentActiveProfile = profileWork,
            targetProfile = profileWork,
            isStrictSessionActive = false
        )
        assertTrue(result is ProfileSwitchDecision.AlreadyActive)
        assertEquals(profileWork, (result as ProfileSwitchDecision.AlreadyActive).profile)
    }

    @Test
    fun testSwitchingWhenNoActiveProfileReturnsImmediateSwitch() {
        val result = engine.evaluateSwitch(
            currentActiveProfile = null,
            targetProfile = profilePersonal,
            isStrictSessionActive = false
        )
        assertTrue(result is ProfileSwitchDecision.ImmediateSwitch)
        assertEquals(profilePersonal, (result as ProfileSwitchDecision.ImmediateSwitch).targetProfile)
    }

    @Test
    fun testSwitchingStandardProfilesReturnsImmediateSwitch() {
        val result = engine.evaluateSwitch(
            currentActiveProfile = profileWork,
            targetProfile = profilePersonal,
            isStrictSessionActive = false
        )
        assertTrue(result is ProfileSwitchDecision.ImmediateSwitch)
        assertEquals(profilePersonal, (result as ProfileSwitchDecision.ImmediateSwitch).targetProfile)
    }

    @Test
    fun testSwitchingWhileStrictSessionIsActiveReturnsBlockedByStrictMode() {
        val result = engine.evaluateSwitch(
            currentActiveProfile = profileStrict,
            targetProfile = profilePersonal,
            isStrictSessionActive = true
        )
        assertTrue(result is ProfileSwitchDecision.BlockedByStrictMode)
        assertEquals(profilePersonal, (result as ProfileSwitchDecision.BlockedByStrictMode).targetProfile)
    }

    @Test
    fun testSwitchingEvenToAnotherProfileWhileStrictModeIsActiveIsBlocked() {
        val result = engine.evaluateSwitch(
            currentActiveProfile = profileWork,
            targetProfile = profileStrict,
            isStrictSessionActive = true
        )
        assertTrue(result is ProfileSwitchDecision.BlockedByStrictMode)
        assertEquals(profileStrict, (result as ProfileSwitchDecision.BlockedByStrictMode).targetProfile)
    }
}
