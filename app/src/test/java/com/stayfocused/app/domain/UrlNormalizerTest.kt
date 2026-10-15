package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun testSchemelessUrlParsing() {
        val parsed = UrlNormalizer.parse("reddit.com/r/all")
        assertTrue(parsed is ParsedOmniboxContent.Url)
        val url = parsed as ParsedOmniboxContent.Url
        assertEquals("reddit.com", url.host)
        assertEquals("/r/all", url.path)
        assertEquals("reddit.com", url.domain)
        assertEquals("https://reddit.com/r/all", url.fullNormalizedUrl)
    }

    @Test
    fun testExplicitHttpsUrlParsing() {
        val parsed = UrlNormalizer.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(parsed is ParsedOmniboxContent.Url)
        val url = parsed as ParsedOmniboxContent.Url
        assertEquals("www.youtube.com", url.host)
        assertEquals("/watch?v=dQw4w9WgXcQ", url.path)
        assertEquals("youtube.com", url.domain)
    }

    @Test
    fun testSearchQueryParsing() {
        val parsed = UrlNormalizer.parse("how to stay focused in monk mode")
        assertTrue(parsed is ParsedOmniboxContent.SearchQuery)
        val query = parsed as ParsedOmniboxContent.SearchQuery
        assertEquals("how to stay focused in monk mode", query.query)
    }

    @Test
    fun testGoogleSearchUrlExtractsSearchQuery() {
        val parsed = UrlNormalizer.parse("https://www.google.com/search?q=how+to+study+for+neet&oq=how+to+study")
        assertTrue(parsed is ParsedOmniboxContent.Url)
        val url = parsed as ParsedOmniboxContent.Url
        assertEquals("how to study for neet", url.searchQuery)
    }

    @Test
    fun testEmptyAndBlankInputs() {
        assertTrue(UrlNormalizer.parse(null) is ParsedOmniboxContent.Empty)
        assertTrue(UrlNormalizer.parse("") is ParsedOmniboxContent.Empty)
        assertTrue(UrlNormalizer.parse("   ") is ParsedOmniboxContent.Empty)
    }

    @Test
    fun testPathBoundaryMatching() {
        assertTrue(UrlNormalizer.matchesPath("/r/all", "/r/all"))
        assertTrue(UrlNormalizer.matchesPath("/r/all/", "/r/all"))
        assertTrue(UrlNormalizer.matchesPath("/r/all?sort=top", "/r/all"))
        assertTrue(UrlNormalizer.matchesPath("/r/all#comments", "/r/all"))
        assertTrue(UrlNormalizer.matchesPath("/r/all/comments/123", "/r/all"))

        // False positive prevention
        assertFalse(UrlNormalizer.matchesPath("/r/all-inclusive", "/r/all"))
        assertFalse(UrlNormalizer.matchesPath("/r/allowlist", "/r/all"))
        assertFalse(UrlNormalizer.matchesPath("/r/allies", "/r/all"))
    }

    @Test
    fun testSafeUrlDecodeMalformedPercentDoesNotCrash() {
        val decoded = UrlNormalizer.safeUrlDecode("search?q=100%legit&val=%ZZ")
        assertNotNull(decoded)
        assertEquals("search?q=100%legit&val=%ZZ", decoded)
    }

    @Test
    fun testStripQueryParams() {
        val clean = UrlNormalizer.stripQueryParams("https://reddit.com/r/androiddev?auth=secretToken123#header")
        assertEquals("https://reddit.com/r/androiddev", clean)
    }

    @Test
    fun testTwoPartTldDomainExtraction() {
        assertEquals("bbc.co.uk", UrlNormalizer.extractRegisteredDomain("www.bbc.co.uk"))
        assertEquals("amazon.co.in", UrlNormalizer.extractRegisteredDomain("sellercentral.amazon.co.in"))
        assertEquals("google.com", UrlNormalizer.extractRegisteredDomain("mail.google.com"))
    }
}
