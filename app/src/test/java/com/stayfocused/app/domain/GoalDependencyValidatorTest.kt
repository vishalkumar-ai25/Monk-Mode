package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.AppLimitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalDependencyValidatorTest {

    @Test
    fun testEmptyOrZeroGoalIsValid() {
        val result1 = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.instagram.android",
            goalPackage = null,
            goalDurationMinutes = 0,
            existingLimits = emptyList()
        )
        assertEquals(GoalValidationResult.Valid, result1)

        val result2 = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.instagram.android",
            goalPackage = "   ",
            goalDurationMinutes = 15,
            existingLimits = emptyList()
        )
        assertEquals(GoalValidationResult.Valid, result2)
    }

    @Test
    fun testSelfDependencyIsInvalid() {
        val result = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.instagram.android",
            goalPackage = "com.instagram.android",
            goalDurationMinutes = 15,
            existingLimits = emptyList()
        )
        assertTrue(result is GoalValidationResult.Invalid)
        val invalid = result as GoalValidationResult.Invalid
        assertTrue(invalid.reason.contains("itself"))
    }

    @Test
    fun testMonkModeAsGoalIsInvalid() {
        val result = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.instagram.android",
            goalPackage = "com.stayfocused.app",
            goalDurationMinutes = 15,
            existingLimits = emptyList()
        )
        assertTrue(result is GoalValidationResult.Invalid)
        val invalid = result as GoalValidationResult.Invalid
        assertTrue(invalid.reason.contains("Monk Mode"))
    }

    @Test
    fun testTwoNodeCycleDetectedAndRejected() {
        // Existing: Reddit requires Instagram
        val redditLimit = AppLimitEntity(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            goalPackageName = "com.instagram.android",
            goalDurationMinutes = 15
        )

        // Proposed: Instagram requires Reddit
        val result = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.instagram.android",
            goalPackage = "com.reddit.frontpage",
            goalDurationMinutes = 15,
            existingLimits = listOf(redditLimit)
        )

        assertTrue("Two-node cycle must be detected and rejected", result is GoalValidationResult.Invalid)
        val invalid = result as GoalValidationResult.Invalid
        assertTrue(invalid.reason.contains("Circular goal dependency detected"))
    }

    @Test
    fun testThreeNodeCycleDetectedAndRejected() {
        // App A -> App B -> App C
        val limitA = AppLimitEntity(
            packageName = "com.app.a",
            appName = "App A",
            goalPackageName = "com.app.b",
            goalDurationMinutes = 15
        )
        val limitB = AppLimitEntity(
            packageName = "com.app.b",
            appName = "App B",
            goalPackageName = "com.app.c",
            goalDurationMinutes = 15
        )

        // Proposed: App C -> App A (completes cycle A -> B -> C -> A)
        val result = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.app.c",
            goalPackage = "com.app.a",
            goalDurationMinutes = 15,
            existingLimits = listOf(limitA, limitB)
        )

        assertTrue("Three-node cycle must be detected and rejected", result is GoalValidationResult.Invalid)
    }

    @Test
    fun testAcyclicDependencyIsValid() {
        // App A -> App B, App C -> App B (no cycle)
        val limitA = AppLimitEntity(
            packageName = "com.app.a",
            appName = "App A",
            goalPackageName = "com.duolingo",
            goalDurationMinutes = 15
        )

        val result = GoalDependencyValidator.validateGoalConfig(
            targetPackage = "com.app.b",
            goalPackage = "com.duolingo",
            goalDurationMinutes = 20,
            existingLimits = listOf(limitA)
        )

        assertEquals(GoalValidationResult.Valid, result)
    }

    @Test
    fun testStrictModeRatchetTighteningIsValid() {
        val existing = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyTimeLimitMinutes = 30,
            dailyLaunchLimit = 10,
            sessionAllowanceMinutes = 20,
            cooldownDurationMinutes = 15,
            goalPackageName = "com.duolingo",
            goalDurationMinutes = 15
        )

        val tightened = existing.copy(
            dailyTimeLimitMinutes = 15, // tightened (30 -> 15)
            dailyLaunchLimit = 5,       // tightened (10 -> 5)
            sessionAllowanceMinutes = 10, // tightened (20 -> 10)
            cooldownDurationMinutes = 30, // tightened (15 -> 30)
            goalDurationMinutes = 30      // tightened (15 -> 30)
        )

        val result = GoalDependencyValidator.validateStrictModeRatchet(existing, tightened)
        assertEquals(RatchetValidationResult.Valid, result)
    }

    @Test
    fun testStrictModeRatchetLooseningTimeLimitIsViolation() {
        val existing = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyTimeLimitMinutes = 30
        )

        // Cannot increase
        val increased = existing.copy(dailyTimeLimitMinutes = 45)
        val res1 = GoalDependencyValidator.validateStrictModeRatchet(existing, increased)
        assertTrue(res1 is RatchetValidationResult.Violation)

        // Cannot remove
        val removed = existing.copy(dailyTimeLimitMinutes = 0)
        val res2 = GoalDependencyValidator.validateStrictModeRatchet(existing, removed)
        assertTrue(res2 is RatchetValidationResult.Violation)
    }

    @Test
    fun testStrictModeRatchetLooseningSessionAllowanceIsViolation() {
        val existing = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            sessionAllowanceMinutes = 15
        )

        val increased = existing.copy(sessionAllowanceMinutes = 30)
        val res = GoalDependencyValidator.validateStrictModeRatchet(existing, increased)
        assertTrue(res is RatchetValidationResult.Violation)
    }

    @Test
    fun testStrictModeRatchetDecreasingCooldownIsViolation() {
        val existing = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            cooldownDurationMinutes = 30
        )

        val decreased = existing.copy(cooldownDurationMinutes = 15)
        val res = GoalDependencyValidator.validateStrictModeRatchet(existing, decreased)
        assertTrue(res is RatchetValidationResult.Violation)
    }

    @Test
    fun testStrictModeRatchetRemovingOrChangingGoalAppIsViolation() {
        val existing = AppLimitEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            goalPackageName = "com.duolingo",
            goalDurationMinutes = 20
        )

        // Cannot nullify
        val nullified = existing.copy(goalPackageName = null)
        val res1 = GoalDependencyValidator.validateStrictModeRatchet(existing, nullified)
        assertTrue(res1 is RatchetValidationResult.Violation)

        // Cannot change package
        val changed = existing.copy(goalPackageName = "com.khanacademy")
        val res2 = GoalDependencyValidator.validateStrictModeRatchet(existing, changed)
        assertTrue(res2 is RatchetValidationResult.Violation)

        // Cannot reduce duration
        val reduced = existing.copy(goalDurationMinutes = 10)
        val res3 = GoalDependencyValidator.validateStrictModeRatchet(existing, reduced)
        assertTrue(res3 is RatchetValidationResult.Violation)
    }
}
