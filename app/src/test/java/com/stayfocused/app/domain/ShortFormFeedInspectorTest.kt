package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.ShortFormRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShortFormFeedInspectorTest {

    private lateinit var inspector: ShortFormFeedInspector

    @Before
    fun setUp() {
        inspector = ShortFormFeedInspector()
    }

    @Test
    fun isMonitoredPackage_correctlyIdentifiesTargetPackages() {
        assertTrue(inspector.isMonitoredPackage("com.google.android.youtube"))
        assertTrue(inspector.isMonitoredPackage("com.instagram.android"))
        assertTrue(inspector.isMonitoredPackage("com.snapchat.android"))
        assertTrue(inspector.isMonitoredPackage("com.facebook.katana"))

        assertFalse(inspector.isMonitoredPackage("com.android.chrome"))
        assertFalse(inspector.isMonitoredPackage("com.whatsapp"))
        assertFalse(inspector.isMonitoredPackage("com.android.settings"))
    }

    @Test
    fun detectFeed_youTubeShorts_matchedByViewId() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_YOUTUBE,
            presentViewIds = setOf("com.google.android.youtube:id/reel_recycler")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_YOUTUBE_SHORTS, feed)
    }

    @Test
    fun detectFeed_youTubeShorts_matchedByTabDescription() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_YOUTUBE,
            selectedTabDescriptions = setOf("Shorts")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_YOUTUBE_SHORTS, feed)
    }

    @Test
    fun detectFeed_youTubeLongFormLecture_isNotDetectedAsShorts() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_YOUTUBE,
            presentViewIds = setOf("com.google.android.youtube:id/watch_while_layout", "com.google.android.youtube:id/player_view"),
            selectedTabDescriptions = setOf("Home", "Subscriptions")
        )
        val feed = inspector.detectFeed(context)
        assertNull(feed)
    }

    @Test
    fun detectFeed_instagramReels_matchedByViewId() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_INSTAGRAM,
            presentViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_INSTAGRAM_REELS, feed)
    }

    @Test
    fun detectFeed_instagramReels_matchedByTabId() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_INSTAGRAM,
            selectedTabIds = setOf("com.instagram.android:id/clips_tab")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_INSTAGRAM_REELS, feed)
    }

    @Test
    fun detectFeed_instagramStories_matchedByViewId() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_INSTAGRAM,
            presentViewIds = setOf("com.instagram.android:id/reel_viewer_root")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_INSTAGRAM_STORIES, feed)
    }

    @Test
    fun detectFeed_instagramDMs_isNotDetectedAsReelsOrStories() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_INSTAGRAM,
            presentViewIds = setOf("com.instagram.android:id/direct_thread_layout"),
            selectedTabDescriptions = setOf("Messages")
        )
        val feed = inspector.detectFeed(context)
        assertNull(feed)
    }

    @Test
    fun detectFeed_snapchatSpotlight_matchedByViewId() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_SNAPCHAT,
            presentViewIds = setOf("com.snapchat.android:id/spotlight_view_container")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_SNAPCHAT_SPOTLIGHT, feed)
    }

    @Test
    fun detectFeed_facebookReels_matchedByViewId() {
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_FACEBOOK,
            presentViewIds = setOf("com.facebook.katana:id/fb_shorts_viewer_container")
        )
        val feed = inspector.detectFeed(context)
        assertEquals(ShortFormFeedInspector.FEED_FACEBOOK_REELS, feed)
    }

    @Test
    fun evaluate_unblockedRule_allowsPlayback() {
        val rules = mapOf(
            ShortFormFeedInspector.FEED_YOUTUBE_SHORTS to ShortFormRuleEntity(
                id = ShortFormFeedInspector.FEED_YOUTUBE_SHORTS,
                packageName = ShortFormFeedInspector.PKG_YOUTUBE,
                displayName = "YouTube Shorts",
                isBlocked = false
            )
        )
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_YOUTUBE,
            presentViewIds = setOf("com.google.android.youtube:id/reel_recycler")
        )
        val decision = inspector.evaluate(context, rules)
        assertTrue(decision is ShortFormFeedInspector.ShortFormDecision.Allow)
    }

    @Test
    fun evaluate_blockedRuleWithZeroLimit_evictsImmediately() {
        val rules = mapOf(
            ShortFormFeedInspector.FEED_YOUTUBE_SHORTS to ShortFormRuleEntity(
                id = ShortFormFeedInspector.FEED_YOUTUBE_SHORTS,
                packageName = ShortFormFeedInspector.PKG_YOUTUBE,
                displayName = "YouTube Shorts",
                isBlocked = true,
                dailyScrollLimit = 0,
                currentDayScrolls = 0
            )
        )
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_YOUTUBE,
            presentViewIds = setOf("com.google.android.youtube:id/reel_recycler")
        )
        val decision = inspector.evaluate(context, rules)
        assertTrue(decision is ShortFormFeedInspector.ShortFormDecision.Evict)
        val evict = decision as ShortFormFeedInspector.ShortFormDecision.Evict
        assertEquals(ShortFormFeedInspector.FEED_YOUTUBE_SHORTS, evict.feedId)
        assertEquals("Home", evict.safeTabDescription)
    }

    @Test
    fun evaluate_blockedRuleWithScrollLimit_allowsUntilLimitReached() {
        val ruleUnderLimit = ShortFormRuleEntity(
            id = ShortFormFeedInspector.FEED_INSTAGRAM_REELS,
            packageName = ShortFormFeedInspector.PKG_INSTAGRAM,
            displayName = "Instagram Reels",
            isBlocked = true,
            dailyScrollLimit = 10,
            currentDayScrolls = 5
        )
        val context = ShortFormViewContext(
            packageName = ShortFormFeedInspector.PKG_INSTAGRAM,
            presentViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager")
        )

        // Under limit: Allow
        val decision1 = inspector.evaluate(context, mapOf(ruleUnderLimit.id to ruleUnderLimit))
        assertTrue(decision1 is ShortFormFeedInspector.ShortFormDecision.Allow)

        // At limit: Evict
        val ruleAtLimit = ruleUnderLimit.copy(currentDayScrolls = 10)
        val decision2 = inspector.evaluate(context, mapOf(ruleAtLimit.id to ruleAtLimit))
        assertTrue(decision2 is ShortFormFeedInspector.ShortFormDecision.Evict)
        val evict = decision2 as ShortFormFeedInspector.ShortFormDecision.Evict
        assertEquals(ShortFormFeedInspector.FEED_INSTAGRAM_REELS, evict.feedId)
        assertEquals("com.instagram.android:id/feed_tab", evict.safeTabId)

        // Over limit: Evict
        val ruleOverLimit = ruleUnderLimit.copy(currentDayScrolls = 15)
        val decision3 = inspector.evaluate(context, mapOf(ruleOverLimit.id to ruleOverLimit))
        assertTrue(decision3 is ShortFormFeedInspector.ShortFormDecision.Evict)
    }

    @Test
    fun getSafeNavigationTarget_returnsExpectedTargets() {
        val (ytTabId, ytTabDesc) = inspector.getSafeNavigationTarget(ShortFormFeedInspector.FEED_YOUTUBE_SHORTS)
        assertNull(ytTabId)
        assertEquals("Home", ytTabDesc)

        val (igTabId, igTabDesc) = inspector.getSafeNavigationTarget(ShortFormFeedInspector.FEED_INSTAGRAM_REELS)
        assertEquals("com.instagram.android:id/feed_tab", igTabId)
        assertEquals("Home", igTabDesc)

        val (snapTabId, snapTabDesc) = inspector.getSafeNavigationTarget(ShortFormFeedInspector.FEED_SNAPCHAT_SPOTLIGHT)
        assertEquals("com.snapchat.android:id/hova_nav_chat", snapTabId)
        assertEquals("Chat", snapTabDesc)
    }
}
