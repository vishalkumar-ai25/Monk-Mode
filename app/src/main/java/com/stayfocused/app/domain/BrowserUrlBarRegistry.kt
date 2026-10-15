package com.stayfocused.app.domain

/**
 * Registry of supported Android web browsers and their address bar view IDs.
 * Pure Kotlin class with zero Android framework dependencies.
 */
data class BrowserConfig(
    val browserName: String,
    val packageName: String,
    val primaryUrlBarId: String,
    val fallbackUrlBarId: String? = null,
    val cctUrlBarId: String? = null,
    val incognitoBadgeId: String? = null,
    val isAlwaysPrivate: Boolean = false
) {
    /**
     * All potential view resource IDs to query for this browser in order of priority.
     */
    val candidateViewIds: List<String> = listOfNotNull(
        primaryUrlBarId,
        cctUrlBarId,
        fallbackUrlBarId
    ).distinct()
}

object BrowserUrlBarRegistry {

    val BROWSERS: List<BrowserConfig> = listOf(
        BrowserConfig(
            browserName = "Google Chrome",
            packageName = "com.android.chrome",
            primaryUrlBarId = "com.android.chrome:id/url_bar",
            fallbackUrlBarId = "url_bar",
            cctUrlBarId = "com.android.chrome:id/title_url",
            incognitoBadgeId = "com.android.chrome:id/incognito_badge"
        ),
        BrowserConfig(
            browserName = "Mozilla Firefox",
            packageName = "org.mozilla.firefox",
            primaryUrlBarId = "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            fallbackUrlBarId = "org.mozilla.firefox:id/url_bar_title",
            cctUrlBarId = null,
            incognitoBadgeId = "org.mozilla.firefox:id/private_browsing_indicator"
        ),
        BrowserConfig(
            browserName = "Brave Browser",
            packageName = "com.brave.browser",
            primaryUrlBarId = "com.brave.browser:id/url_bar",
            fallbackUrlBarId = "url_bar",
            cctUrlBarId = "com.brave.browser:id/title_url",
            incognitoBadgeId = "com.brave.browser:id/incognito_badge"
        ),
        BrowserConfig(
            browserName = "Samsung Internet",
            packageName = "com.sec.android.app.sbrowser",
            primaryUrlBarId = "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            fallbackUrlBarId = "com.sec.android.app.sbrowser:id/location_bar",
            cctUrlBarId = null,
            incognitoBadgeId = "com.sec.android.app.sbrowser:id/secret_mode_icon"
        ),
        BrowserConfig(
            browserName = "Microsoft Edge",
            packageName = "com.microsoft.emmx",
            primaryUrlBarId = "com.microsoft.emmx:id/url_bar",
            fallbackUrlBarId = "com.microsoft.emmx:id/search_box",
            cctUrlBarId = "com.microsoft.emmx:id/title_url",
            incognitoBadgeId = "com.microsoft.emmx:id/inprivate_icon"
        ),
        BrowserConfig(
            browserName = "Opera Browser",
            packageName = "com.opera.browser",
            primaryUrlBarId = "com.opera.browser:id/url_field",
            fallbackUrlBarId = "com.opera.browser:id/url_bar",
            cctUrlBarId = null,
            incognitoBadgeId = "com.opera.browser:id/private_mode_icon"
        ),
        BrowserConfig(
            browserName = "Opera Mini",
            packageName = "com.opera.mini.native",
            primaryUrlBarId = "com.opera.mini.native:id/url_field",
            fallbackUrlBarId = "url_field",
            cctUrlBarId = null,
            incognitoBadgeId = null
        ),
        BrowserConfig(
            browserName = "DuckDuckGo",
            packageName = "com.duckduckgo.mobile.android",
            primaryUrlBarId = "com.duckduckgo.mobile.android:id/omnibarTextInput",
            fallbackUrlBarId = "omnibarTextInput",
            cctUrlBarId = null,
            incognitoBadgeId = null
        ),
        BrowserConfig(
            browserName = "Kiwi Browser",
            packageName = "com.kiwibrowser.browser",
            primaryUrlBarId = "com.kiwibrowser.browser:id/url_bar",
            fallbackUrlBarId = "url_bar",
            cctUrlBarId = "com.kiwibrowser.browser:id/title_url",
            incognitoBadgeId = "com.kiwibrowser.browser:id/incognito_badge"
        ),
        BrowserConfig(
            browserName = "Vivaldi",
            packageName = "com.vivaldi.browser",
            primaryUrlBarId = "com.vivaldi.browser:id/url_bar",
            fallbackUrlBarId = "url_bar",
            cctUrlBarId = "com.vivaldi.browser:id/title_url",
            incognitoBadgeId = "com.vivaldi.browser:id/incognito_badge"
        ),
        BrowserConfig(
            browserName = "Yandex Browser",
            packageName = "com.yandex.browser",
            primaryUrlBarId = "com.yandex.browser:id/bro_omnibox_address_title_text",
            fallbackUrlBarId = "bro_omnibox_address_title_text",
            cctUrlBarId = null,
            incognitoBadgeId = null
        ),
        BrowserConfig(
            browserName = "Ecosia",
            packageName = "com.ecosia.android",
            primaryUrlBarId = "com.ecosia.android:id/url_bar",
            fallbackUrlBarId = "url_bar",
            cctUrlBarId = "com.ecosia.android:id/title_url",
            incognitoBadgeId = "com.ecosia.android:id/incognito_badge"
        ),
        BrowserConfig(
            browserName = "Tor Browser",
            packageName = "org.torproject.torbrowser",
            primaryUrlBarId = "org.torproject.torbrowser:id/mozac_browser_toolbar_url_view",
            fallbackUrlBarId = "org.torproject.torbrowser:id/url_bar_title",
            cctUrlBarId = null,
            incognitoBadgeId = null,
            isAlwaysPrivate = true
        )
    )

    private val configMap: Map<String, BrowserConfig> = BROWSERS.associateBy { it.packageName }

    val SUPPORTED_PACKAGES: Set<String> = configMap.keys

    fun isSupportedBrowser(packageName: String?): Boolean {
        if (packageName == null) return false
        return configMap.containsKey(packageName)
    }

    fun getBrowserConfig(packageName: String?): BrowserConfig? {
        if (packageName == null) return false.let { null }
        return configMap[packageName]
    }

    fun getAllCandidateIds(packageName: String): List<String> {
        return configMap[packageName]?.candidateViewIds ?: emptyList()
    }
}
