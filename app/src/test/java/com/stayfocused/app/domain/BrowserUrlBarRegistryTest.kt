package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlBarRegistryTest {

    @Test
    fun testAll12PlusMajorBrowsersAreRegistered() {
        val expectedPackages = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.kiwibrowser.browser",
            "com.vivaldi.browser",
            "com.yandex.browser",
            "com.ecosia.android",
            "org.torproject.torbrowser"
        )

        for (pkg in expectedPackages) {
            assertTrue("Expected $pkg to be supported", BrowserUrlBarRegistry.isSupportedBrowser(pkg))
            val config = BrowserUrlBarRegistry.getBrowserConfig(pkg)
            assertNotNull("Config for $pkg must not be null", config)
            assertTrue("Candidate IDs for $pkg must not be empty", config!!.candidateViewIds.isNotEmpty())
        }
    }

    @Test
    fun testChromeHasCctAndUrlBarIds() {
        val config = BrowserUrlBarRegistry.getBrowserConfig("com.android.chrome")
        assertNotNull(config)
        assertEquals("com.android.chrome:id/url_bar", config!!.primaryUrlBarId)
        assertEquals("com.android.chrome:id/title_url", config.cctUrlBarId)
        assertEquals("url_bar", config.fallbackUrlBarId)
        assertTrue(config.candidateViewIds.contains("com.android.chrome:id/url_bar"))
        assertTrue(config.candidateViewIds.contains("com.android.chrome:id/title_url"))
    }

    @Test
    fun testUnsupportedAppReturnsFalseAndNull() {
        assertFalse(BrowserUrlBarRegistry.isSupportedBrowser("com.instagram.android"))
        assertFalse(BrowserUrlBarRegistry.isSupportedBrowser("com.whatsapp"))
        assertFalse(BrowserUrlBarRegistry.isSupportedBrowser(null))
        assertNull(BrowserUrlBarRegistry.getBrowserConfig("com.instagram.android"))
        assertNull(BrowserUrlBarRegistry.getBrowserConfig(null))
    }
}
