package com.stayfocused.app

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseBuildVariantTest {

    @Test
    fun testReleaseBuildVariantHasAntiTamperEnabled() {
        assertNotNull(BuildConfig.ANTI_TAMPER_ENABLED)
        assertTrue(
            "Release build variant must have ANTI_TAMPER_ENABLED = true for daily self-control",
            BuildConfig.ANTI_TAMPER_ENABLED
        )
    }
}
