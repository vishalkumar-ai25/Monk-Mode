package com.stayfocused.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BuildVariantTest {

    @Test
    fun testDebugBuildVariantHasAntiTamperDisabled() {
        // In the debug build variant, anti-tamper must be disabled (false)
        // to protect the developer from getting locked out of their dev loop.
        assertNotNull(BuildConfig.ANTI_TAMPER_ENABLED)
        assertFalse(
            "Debug build variant must have ANTI_TAMPER_ENABLED = false",
            BuildConfig.ANTI_TAMPER_ENABLED
        )
    }
}
