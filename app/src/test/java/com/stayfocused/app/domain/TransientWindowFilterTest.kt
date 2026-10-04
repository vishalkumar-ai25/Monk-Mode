package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TransientWindowFilter].
 *
 * All tests use injected IME-package lambdas and [imeCacheTtlMs]=0 so results are
 * immediate (no TTL wait), and there is zero Android framework dependency.
 * The entire suite runs as pure JVM.
 */
class TransientWindowFilterTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun filter(
        imePackages: Set<String> = emptySet(),
        selfPackage: String = "com.stayfocused.app"
    ) = TransientWindowFilter(
        imePackagesProvider = { imePackages },
        selfPackage = selfPackage,
        imeCacheTtlMs = 0L   // disable TTL so injected lambda is always consulted
    )

    // ── Own overlay windows ───────────────────────────────────────────────────

    @Test
    fun `own app package is transient`() {
        assertTrue(filter().isTransient("com.stayfocused.app", null))
    }

    @Test
    fun `own app package is case-insensitively transient`() {
        assertTrue(filter().isTransient("COM.STAYFOCUSED.APP", null))
    }

    @Test
    fun `own app package with class name is transient`() {
        assertTrue(filter().isTransient("com.stayfocused.app", "com.stayfocused.app.ui.overlay.BlockOverlayView"))
    }

    @Test
    fun `custom selfPackage variant is transient`() {
        val f = filter(selfPackage = "com.stayfocused.app.debug")
        assertTrue(f.isTransient("com.stayfocused.app.debug", null))
        assertFalse(f.isTransient("com.stayfocused.app", null))
    }

    // ── System UI ─────────────────────────────────────────────────────────────

    @Test
    fun `AOSP systemui is transient`() {
        assertTrue(filter().isTransient("com.android.systemui", null))
    }

    @Test
    fun `Samsung systemui is transient`() {
        assertTrue(filter().isTransient("com.samsung.android.systemui", "com.samsung.systemui.VolumePanel"))
    }

    @Test
    fun `MIUI systemui is transient`() {
        assertTrue(filter().isTransient("com.miui.systemui", null))
    }

    @Test
    fun `OPPO systemui is transient`() {
        assertTrue(filter().isTransient("com.oppo.systemui", null))
    }

    @Test
    fun `OnePlus systemui is transient`() {
        assertTrue(filter().isTransient("com.oplus.systemui", null))
    }

    @Test
    fun `Vivo systemui is transient`() {
        assertTrue(filter().isTransient("com.vivo.systemui", null))
    }

    @Test
    fun `Honor systemui is transient`() {
        assertTrue(filter().isTransient("com.hihonor.systemui", null))
    }

    @Test
    fun `ASUS ZenUI systemui is transient`() {
        assertTrue(filter().isTransient("com.asus.systemui", null))
    }

    @Test
    fun `Nothing OS systemui is transient`() {
        assertTrue(filter().isTransient("com.nothing.systemui", null))
    }

    // ── IME packages (injected at runtime) ────────────────────────────────────

    @Test
    fun `enabled IME package is transient`() {
        val imeFilter = filter(imePackages = setOf("com.google.android.inputmethod.latin"))
        assertTrue(imeFilter.isTransient("com.google.android.inputmethod.latin", null))
    }

    @Test
    fun `non-IME package not in enabled list is NOT transient`() {
        val imeFilter = filter(imePackages = setOf("com.google.android.inputmethod.latin"))
        assertFalse(imeFilter.isTransient("com.example.someapp", null))
    }

    @Test
    fun `empty IME set does not classify random package as transient`() {
        assertFalse(filter(imePackages = emptySet()).isTransient("com.swiftkey.android", null))
    }

    // ── IME TTL cache ─────────────────────────────────────────────────────────

    @Test
    fun `IME package recognized with TTL=0 on every call`() {
        var callCount = 0
        val f = TransientWindowFilter(
            imePackagesProvider = { callCount++; setOf("com.test.ime") },
            selfPackage = "com.stayfocused.app",
            imeCacheTtlMs = 0L
        )
        assertTrue(f.isTransient("com.test.ime", null))
        assertTrue(f.isTransient("com.test.ime", null))
        // With TTL=0 the lambda is called each time (both calls reach it)
        assertTrue("Provider should be called for every isTransient with TTL=0", callCount >= 2)
    }

    @Test
    fun `IME package provider is cached within TTL`() {
        var callCount = 0
        val f = TransientWindowFilter(
            imePackagesProvider = { callCount++; setOf("com.test.ime") },
            selfPackage = "com.stayfocused.app",
            imeCacheTtlMs = 10_000L
        )
        assertTrue(f.isTransient("com.test.ime", null))
        assertTrue(f.isTransient("com.test.ime", null))
        assertEquals("Provider should only be invoked once within TTL", 1, callCount)
    }

    // ── Permission-controller packages ────────────────────────────────────────

    @Test
    fun `AOSP permission controller package is transient`() {
        assertTrue(filter().isTransient("com.android.permissioncontroller", null))
    }

    @Test
    fun `Google permission controller package is transient`() {
        assertTrue(filter().isTransient("com.google.android.permissioncontroller", null))
    }

    @Test
    fun `Samsung permission controller package is transient even when className is null`() {
        assertTrue(filter().isTransient("com.samsung.android.permissioncontroller", null))
    }

    // ── Class-name heuristic (OEM-renamed permission dialogs) ─────────────────

    @Test
    fun `GrantPermissionsActivity class in unknown OEM package is transient`() {
        assertTrue(filter().isTransient("com.oem.vendor.permission", "com.oem.vendor.permission.GrantPermissionsActivity"))
    }

    @Test
    fun `ReviewPermissionsActivity class in unknown package is transient`() {
        assertTrue(filter().isTransient("com.oem.custom", "com.oem.custom.permission.ReviewPermissionsActivity"))
    }

    // ── Overlap: class fragment must NOT produce false positive from pkg check ─

    @Test
    fun `Google perm controller package is caught by pkg check, not class heuristic only`() {
        // Both checks 4 and 5 would cover this; ensure neither causes a bug
        assertTrue(filter().isTransient("com.google.android.permissioncontroller", "com.google.android.permissioncontroller.GrantPermissionsActivity"))
    }

    // ── Real apps must NOT be classified as transient ─────────────────────────

    @Test
    fun `regular social media app is NOT transient`() {
        assertFalse(filter().isTransient("com.instagram.android", null))
    }

    @Test
    fun `browser is NOT transient`() {
        assertFalse(filter().isTransient("com.android.chrome", null))
    }

    @Test
    fun `calculator is NOT transient`() {
        assertFalse(filter().isTransient("com.google.android.calculator", null))
    }

    /**
     * Settings is NOT transient — it is handled by the separate [SettingsTamperInspector]
     * branch in [FocusAccessibilityService], which runs after the transient guard.
     */
    @Test
    fun `settings package is NOT transient`() {
        assertFalse(filter().isTransient("com.android.settings", "com.android.settings.wifi.WifiSettings"))
    }
}
