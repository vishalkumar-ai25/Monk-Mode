package com.stayfocused.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class DebugBuildVariantTest {

    @Test
    fun testDebugBuildVariantHasAntiTamperDisabled() {
        assertNotNull(BuildConfig.ANTI_TAMPER_ENABLED)
        assertFalse(
            "Debug build variant must have ANTI_TAMPER_ENABLED = false to protect developer loop",
            BuildConfig.ANTI_TAMPER_ENABLED
        )
    }
}
