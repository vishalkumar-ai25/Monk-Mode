package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAddressBarInspectorTest {

    private val testMatcher = AhoCorasickKeywordMatcher.create(
        customKeywords = listOf("vape", "casino", "gambling"),
        blockedPaths = listOf("/r/all", "youtube.com/feed/trending", "reddit.com/r/special"),
        includeAdultDictionary = true
    )

    private val blockedDomains = setOf("facebook.com", "instagram.com", "tiktok.com")
    private val blockedPaths = listOf("/r/all", "youtube.com/feed/trending", "reddit.com/r/special")

    private val inspector = BrowserAddressBarInspector(
        ahoCorasickMatcherProvider = { testMatcher },
        blockedDomainsProvider = { blockedDomains },
        blockedPathsProvider = { blockedPaths }
    )

    @Test
    fun testNonBrowserPackageIgnored() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.whatsapp",
                addressBarText = "https://facebook.com"
            )
        )
        assertEquals(BrowserInspectionResult.IgnoredNotBrowser, result)
    }

    @Test
    fun testTypingInOmniboxReturnsTyping() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "facebook.com",
                isFocused = true,
                isFocusable = true
            )
        )
        assertEquals(BrowserInspectionResult.TypingInOmnibox, result)
    }

    @Test
    fun testReadOnlyCctInspectedNormally() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "facebook.com",
                isFocused = false,
                isFocusable = false
            )
        )
        assertTrue(result is BrowserInspectionResult.Blocked)
        val blocked = result as BrowserInspectionResult.Blocked
        assertEquals("facebook.com", blocked.target)
    }

    @Test
    fun testDomainBlocked() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "m.instagram.com/p/123",
                isFocused = false
            )
        )
        assertTrue(result is BrowserInspectionResult.Blocked)
        val blocked = result as BrowserInspectionResult.Blocked
        assertEquals("instagram.com", blocked.target)
        assertEquals("DOMAIN", (blocked.reason).matchType)
    }

    @Test
    fun testPathBlockedOnMatchingSubpath() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "reddit.com/r/all",
                isFocused = false
            )
        )
        assertTrue(result is BrowserInspectionResult.Blocked)
        val blocked = result as BrowserInspectionResult.Blocked
        assertEquals("PATH", (blocked.reason).matchType)
    }

    @Test
    fun testAllowedPathOnSameDomain() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "reddit.com/r/androiddev",
                isFocused = false
            )
        )
        assertTrue(result is BrowserInspectionResult.Allowed)
    }

    @Test
    fun testKeywordSearchBlocked() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "https://www.google.com/search?q=buy+vape+online",
                isFocused = false
            )
        )
        assertTrue(result is BrowserInspectionResult.Blocked)
        val blocked = result as BrowserInspectionResult.Blocked
        assertEquals("CUSTOM", (blocked.reason).matchType)
        assertEquals("vape", (blocked.reason).matchedRule)
    }

    @Test
    fun testAdultDictionaryBlocked() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.brave.browser",
                addressBarText = "https://pornhub.com",
                isFocused = false
            )
        )
        assertTrue(result is BrowserInspectionResult.Blocked)
        val blocked = result as BrowserInspectionResult.Blocked
        assertEquals("ADULT", (blocked.reason).matchType)
    }

    @Test
    fun testIncognitoStatePreserved() {
        val result = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "wikipedia.org/wiki/Kotlin",
                isFocused = false,
                isIncognito = true
            )
        )
        assertTrue(result is BrowserInspectionResult.Allowed)
        val allowed = result as BrowserInspectionResult.Allowed
        assertTrue(allowed.isIncognito)
    }

    @Test
    fun testDomainSuffixBoundaryPreventsFalsePositiveOnSubstrings() {
        // reddit.com/r/special is blocked
        val blockedResult = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "https://reddit.com/r/special",
                isFocused = false
            )
        )
        assertTrue(blockedResult is BrowserInspectionResult.Blocked)

        // notreddit.com/r/special must NOT be blocked by reddit.com/r/special rule
        val allowedResult = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "https://notreddit.com/r/special",
                isFocused = false
            )
        )
        assertTrue(allowedResult is BrowserInspectionResult.Allowed)
    }

    @Test
    fun testPercentEncodedKeywordBlocked() {
        // %6e%73%66%77 is "nsfw"
        val nsfwResult = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "https://example.com/topic/%6e%73%66%77",
                isFocused = false
            )
        )
        assertTrue(nsfwResult is BrowserInspectionResult.Blocked)
        val blockedNsfw = nsfwResult as BrowserInspectionResult.Blocked
        assertEquals("ADULT", (blockedNsfw.reason).matchType)

        // %76%61%70%65 is "vape" (custom keyword)
        val vapeResult = inspector.inspect(
            BrowserAddressBarViewContext(
                packageName = "com.android.chrome",
                addressBarText = "https://www.google.com/search?q=%76%61%70%65",
                isFocused = false
            )
        )
        assertTrue(vapeResult is BrowserInspectionResult.Blocked)
        val blockedVape = vapeResult as BrowserInspectionResult.Blocked
        assertEquals("CUSTOM", (blockedVape.reason).matchType)
    }
}
