package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.ShortFormRuleEntity

/**
 * Contextual view snapshot extracted from an accessibility event or window.
 * Strictly captures resource IDs and navigation bar selection states.
 * Contains ZERO user content, comments, captions, or private message texts.
 */
data class ShortFormViewContext(
    val packageName: String,
    val className: String? = null,
    val presentViewIds: Set<String> = emptySet(),
    val selectedTabDescriptions: Set<String> = emptySet(),
    val selectedTabIds: Set<String> = emptySet()
)

/**
 * Pure Kotlin domain engine for detecting granular short-form video feeds and evaluating eviction decisions.
 * Zero Android UI or framework dependencies to ensure 100% JVM testability.
 */
class ShortFormFeedInspector {

    sealed class ShortFormDecision {
        data object Allow : ShortFormDecision()
        data class Evict(
            val feedId: String,
            val rule: ShortFormRuleEntity,
            val safeTabId: String? = null,
            val safeTabDescription: String? = null
        ) : ShortFormDecision()
    }

    companion object {
        const val FEED_YOUTUBE_SHORTS = "youtube_shorts"
        const val FEED_INSTAGRAM_REELS = "instagram_reels"
        const val FEED_INSTAGRAM_STORIES = "instagram_stories"
        const val FEED_SNAPCHAT_SPOTLIGHT = "snapchat_spotlight"
        const val FEED_FACEBOOK_REELS = "facebook_reels"

        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_SNAPCHAT = "com.snapchat.android"
        const val PKG_FACEBOOK = "com.facebook.katana"

        val SHORT_FORM_PACKAGES = setOf(
            PKG_YOUTUBE,
            PKG_INSTAGRAM,
            PKG_SNAPCHAT,
            PKG_FACEBOOK
        )

        // YouTube Shorts view signatures
        val YOUTUBE_SHORTS_VIEW_IDS = setOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_player_page_container",
            "reel_recycler",
            "reel_watch_fragment_root"
        )
        val YOUTUBE_SHORTS_TAB_DESCRIPTIONS = setOf("shorts")

        // Instagram Reels view signatures
        val INSTAGRAM_REELS_VIEW_IDS = setOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_video_container",
            "clips_viewer_view_pager",
            "clips_video_container"
        )
        val INSTAGRAM_REELS_TAB_IDS = setOf(
            "com.instagram.android:id/clips_tab",
            "clips_tab"
        )
        val INSTAGRAM_REELS_TAB_DESCRIPTIONS = setOf("reels")

        // Instagram Stories view signatures
        val INSTAGRAM_STORIES_VIEW_IDS = setOf(
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/story_viewer_container",
            "reel_viewer_root",
            "story_viewer_container"
        )

        // Snapchat Spotlight view signatures
        val SNAPCHAT_SPOTLIGHT_VIEW_IDS = setOf(
            "com.snapchat.android:id/spotlight_view_container",
            "spotlight_view_container"
        )
        val SNAPCHAT_SPOTLIGHT_TAB_DESCRIPTIONS = setOf("spotlight")

        // Facebook Reels view signatures
        val FACEBOOK_REELS_VIEW_IDS = setOf(
            "com.facebook.katana:id/fb_shorts_viewer_container",
            "fb_shorts_viewer_container"
        )
    }

    /**
     * Checks if a package is one of the monitored media applications.
     */
    fun isMonitoredPackage(packageName: String): Boolean {
        return SHORT_FORM_PACKAGES.contains(packageName.trim().lowercase())
    }

    /**
     * Detects if the provided view context matches a known short-form video feed signature.
     * Returns the matching feedId, or null if no signature matches.
     */
    fun detectFeed(context: ShortFormViewContext): String? {
        val pkg = context.packageName.trim().lowercase()
        if (!isMonitoredPackage(pkg)) return null

        val normalizedIds = context.presentViewIds.map { it.lowercase() }.toSet()
        val normalizedTabDescs = context.selectedTabDescriptions.map { it.lowercase() }.toSet()
        val normalizedTabIds = context.selectedTabIds.map { it.lowercase() }.toSet()

        return when (pkg) {
            PKG_YOUTUBE -> {
                val matchesViewId = normalizedIds.any { id -> YOUTUBE_SHORTS_VIEW_IDS.any { sig -> id.contains(sig) } }
                val matchesTabDesc = normalizedTabDescs.any { desc -> YOUTUBE_SHORTS_TAB_DESCRIPTIONS.contains(desc) }
                if (matchesViewId || matchesTabDesc) FEED_YOUTUBE_SHORTS else null
            }
            PKG_INSTAGRAM -> {
                // Check Stories first
                val matchesStories = normalizedIds.any { id -> INSTAGRAM_STORIES_VIEW_IDS.any { sig -> id.contains(sig) } }
                if (matchesStories) {
                    FEED_INSTAGRAM_STORIES
                } else {
                    val matchesReelsViewId = normalizedIds.any { id -> INSTAGRAM_REELS_VIEW_IDS.any { sig -> id.contains(sig) } }
                    val matchesReelsTabId = normalizedTabIds.any { id -> INSTAGRAM_REELS_TAB_IDS.any { sig -> id.contains(sig) } }
                    val matchesReelsTabDesc = normalizedTabDescs.any { desc -> INSTAGRAM_REELS_TAB_DESCRIPTIONS.contains(desc) }
                    if (matchesReelsViewId || matchesReelsTabId || matchesReelsTabDesc) FEED_INSTAGRAM_REELS else null
                }
            }
            PKG_SNAPCHAT -> {
                val matchesSpotlightViewId = normalizedIds.any { id -> SNAPCHAT_SPOTLIGHT_VIEW_IDS.any { sig -> id.contains(sig) } }
                val matchesSpotlightTabDesc = normalizedTabDescs.any { desc -> SNAPCHAT_SPOTLIGHT_TAB_DESCRIPTIONS.contains(desc) }
                if (matchesSpotlightViewId || matchesSpotlightTabDesc) FEED_SNAPCHAT_SPOTLIGHT else null
            }
            PKG_FACEBOOK -> {
                val matchesFbReels = normalizedIds.any { id -> FACEBOOK_REELS_VIEW_IDS.any { sig -> id.contains(sig) } }
                if (matchesFbReels) FEED_FACEBOOK_REELS else null
            }
            else -> null
        }
    }

    /**
     * Evaluates whether an eviction should occur based on detected feed and in-memory rule state.
     */
    fun evaluate(
        context: ShortFormViewContext,
        rules: Map<String, ShortFormRuleEntity>
    ): ShortFormDecision {
        val feedId = detectFeed(context) ?: return ShortFormDecision.Allow
        val rule = rules[feedId] ?: return ShortFormDecision.Allow

        if (!rule.isBlocked) {
            return ShortFormDecision.Allow
        }

        val shouldEvict = rule.dailyScrollLimit == 0 || rule.currentDayScrolls >= rule.dailyScrollLimit
        if (!shouldEvict) {
            return ShortFormDecision.Allow
        }

        val safeTarget = getSafeNavigationTarget(feedId)
        return ShortFormDecision.Evict(
            feedId = feedId,
            rule = rule,
            safeTabId = safeTarget.first,
            safeTabDescription = safeTarget.second
        )
    }

    /**
     * Resolves the verified safe tab target (Home/Chat) to avoid dumping the user to the Android Launcher.
     */
    fun getSafeNavigationTarget(feedId: String): Pair<String?, String?> {
        return when (feedId) {
            FEED_YOUTUBE_SHORTS -> Pair(null, "Home")
            FEED_INSTAGRAM_REELS, FEED_INSTAGRAM_STORIES -> Pair("com.instagram.android:id/feed_tab", "Home")
            FEED_SNAPCHAT_SPOTLIGHT -> Pair("com.snapchat.android:id/hova_nav_chat", "Chat")
            FEED_FACEBOOK_REELS -> Pair(null, "Home")
            else -> Pair(null, null)
        }
    }
}
