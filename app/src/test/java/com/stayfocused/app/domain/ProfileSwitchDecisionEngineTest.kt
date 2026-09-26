package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProfileSwitchDecisionEngineTest {

    private lateinit var engine: ProfileSwitchDecisionEngine

    @Before
    fun setUp() {
        engine = ProfileSwitchDecisionEngine()
    }

    @Test
    fun testSwitchFromNullActiveProfileReturnsImmediateSwitch() {
        val target = FocusProfileEntity(id = 1, name = "Deep Work", isActive = false, isStrictMode = false)

        val decision = engine.evaluateSwitch(currentActiveProfile = null, targetProfile = target)

        assertTrue(decision is ProfileSwitchDecision.ImmediateSwitch)
        assertEquals(target, (decision as ProfileSwitchDecision.ImmediateSwitch).targetProfile)
    }

    @Test
    fun testSwitchFromNonStrictActiveProfileReturnsImmediateSwitch() {
        val current = FocusProfileEntity(id = 1, name = "Reading", isActive = true, isStrictMode = false)
        val target = FocusProfileEntity(id = 2, name = "Deep Work", isActive = false, isStrictMode = true)

        val decision = engine.evaluateSwitch(currentActiveProfile = current, targetProfile = target)

        assertTrue(decision is ProfileSwitchDecision.ImmediateSwitch)
        assertEquals(target, (decision as ProfileSwitchDecision.ImmediateSwitch).targetProfile)
    }

    @Test
    fun testSwitchToAlreadyActiveProfileReturnsAlreadyActive() {
        val current = FocusProfileEntity(id = 1, name = "Deep Work", isActive = true, isStrictMode = false)
        val target = FocusProfileEntity(id = 1, name = "Deep Work", isActive = true, isStrictMode = false)

        val decision = engine.evaluateSwitch(currentActiveProfile = current, targetProfile = target)

        assertTrue(decision is ProfileSwitchDecision.AlreadyActive)
        assertEquals(target, (decision as ProfileSwitchDecision.AlreadyActive).profile)
    }

    @Test
    fun testSwitchFromStrictModeProfileRequiresConfirmation() {
        val current = FocusProfileEntity(id = 1, name = "Strict Monk", isActive = true, isStrictMode = true)
        val target = FocusProfileEntity(id = 2, name = "Casual Reading", isActive = false, isStrictMode = false)

        val decision = engine.evaluateSwitch(currentActiveProfile = current, targetProfile = target)

        assertTrue(decision is ProfileSwitchDecision.RequiresConfirmation)
        val confirmation = decision as ProfileSwitchDecision.RequiresConfirmation
        assertEquals(current, confirmation.outgoingProfile)
        assertEquals(target, confirmation.targetProfile)
    }

    @Test
    fun testSwitchToSameStrictModeProfileReturnsAlreadyActive() {
        val current = FocusProfileEntity(id = 1, name = "Strict Monk", isActive = true, isStrictMode = true)
        val target = FocusProfileEntity(id = 1, name = "Strict Monk", isActive = true, isStrictMode = true)

        val decision = engine.evaluateSwitch(currentActiveProfile = current, targetProfile = target)

        assertTrue(decision is ProfileSwitchDecision.AlreadyActive)
    }
}
