package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AhoCorasickKeywordMatcherTest {

    @Test
    fun testAdultKeywordsDetectedInUrls() {
        val matcher = AhoCorasickKeywordMatcher.create(
            customKeywords = emptyList(),
            blockedPaths = emptyList(),
            includeAdultDictionary = true
        )

        assertTrue(matcher.isBlocked("https://www.pornhub.com/video"))
        assertTrue(matcher.isBlocked("xvideos.com"))
        assertTrue(matcher.isBlocked("reddit.com/r/nsfw"))
        assertTrue(matcher.isBlocked("https://example.com/search?q=hentai"))
        assertTrue(matcher.isBlocked("xxx.com"))

        val match = matcher.findFirstMatch("https://www.pornhub.com/video")
        assertNotNull(match)
        assertEquals("pornhub", match!!.keyword)
        assertEquals(KeywordCategory.ADULT, match.category)
    }

    @Test
    fun testScunthorpeCatastrophePrevention() {
        val customKeywords = listOf("ass", "anal", "sex", "tit", "cock")
        val matcher = AhoCorasickKeywordMatcher.create(
            customKeywords = customKeywords,
            blockedPaths = emptyList(),
            includeAdultDictionary = false
        )

        // Substring collisions that MUST NOT be blocked:
        assertFalse("class must not match ass", matcher.isBlocked("https://stackoverflow.com/questions/123/java-class"))
        assertFalse("passwords must not match ass", matcher.isBlocked("https://passwords.google.com"))
        assertFalse("assets must not match ass", matcher.isBlocked("https://github.com/user/repo/assets/img.png"))
        assertFalse("compass must not match ass", matcher.isBlocked("https://compass.com"))
        assertFalse("grass must not match ass", matcher.isBlocked("https://grassroots.org"))

        assertFalse("analytics must not match anal", matcher.isBlocked("https://analytics.google.com"))
        assertFalse("analog must not match anal", matcher.isBlocked("https://analog.com"))
        assertFalse("canal must not match anal", matcher.isBlocked("https://panamacanal.com"))

        assertFalse("sussex must not match sex", matcher.isBlocked("https://sussex.ac.uk"))
        assertFalse("essex must not match sex", matcher.isBlocked("https://essex.gov.uk"))
        assertFalse("sextant must not match sex", matcher.isBlocked("https://dictionary.com/browse/sextant"))

        assertFalse("title must not match tit", matcher.isBlocked("https://example.com/title"))
        assertFalse("institution must not match tit", matcher.isBlocked("https://institution.org"))

        assertFalse("peacock must not match cock", matcher.isBlocked("https://peacocktv.com"))
        assertFalse("cocktail must not match cock", matcher.isBlocked("https://cocktailrecipes.com"))

        // Genuine delimited matches that MUST be blocked:
        assertTrue("isolated ass with delimiters must match", matcher.isBlocked("https://example.com/ass/test"))
        assertTrue("query param ass must match", matcher.isBlocked("https://google.com/search?q=ass"))
        assertTrue("isolated sex must match", matcher.isBlocked("https://sex.com"))
        assertTrue("delimited sex must match", matcher.isBlocked("https://example.com/watch/sex/now"))
    }

    @Test
    fun testCustomKeywordsAndBlockedPaths() {
        val matcher = AhoCorasickKeywordMatcher.create(
            customKeywords = listOf("casino", "gambling", "vape"),
            blockedPaths = listOf("/r/all", "/feed/trending"),
            includeAdultDictionary = false
        )

        assertTrue(matcher.isBlocked("https://online-casino.com/play"))
        assertTrue(matcher.isBlocked("https://google.com/search?q=buy+vape"))
        assertTrue(matcher.isBlocked("reddit.com/r/all"))
        assertTrue(matcher.isBlocked("https://youtube.com/feed/trending"))

        assertFalse(matcher.isBlocked("https://reddit.com/r/androiddev"))
        assertFalse(matcher.isBlocked("https://youtube.com/watch?v=123"))
    }

    @Test
    fun testLinearExecutionSpeedAcrossLargeIterations() {
        val matcher = AhoCorasickKeywordMatcher.create(
            customKeywords = listOf("distraction", "procrastinate", "socialmedia", "memes", "shorts"),
            blockedPaths = listOf("/explore", "/trending", "/shorts"),
            includeAdultDictionary = true
        )

        val testUrl = "https://www.google.com/search?q=how+to+improve+focus+and+avoid+distraction+in+modern+workplaces"
        // Warm up JIT
        for (i in 0 until 100) {
            matcher.findFirstMatch(testUrl)
        }

        val start = System.nanoTime()
        for (i in 0 until 1000) {
            matcher.findFirstMatch(testUrl)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        assertTrue("1000 keyword searches must complete in < 50ms (was ${elapsedMs}ms)", elapsedMs < 50.0)
    }

    @Test
    fun testMasturbationAndMasturbatingDetectedInAdultDictionary() {
        val matcher = AhoCorasickKeywordMatcher.create(
            customKeywords = emptyList(),
            blockedPaths = emptyList(),
            includeAdultDictionary = true
        )

        assertTrue(matcher.isBlocked("https://example.com/masturbation-tips"))
        assertTrue(matcher.isBlocked("https://example.com/masturbating/video"))
        assertTrue(matcher.isBlocked("https://example.com/how-to-masturbate"))

        val match1 = matcher.findFirstMatch("https://example.com/masturbation")
        assertNotNull(match1)
        assertEquals("masturbation", match1?.keyword)

        val match2 = matcher.findFirstMatch("https://example.com/masturbating")
        assertNotNull(match2)
        assertEquals("masturbating", match2?.keyword)
    }
}
