package com.stayfocused.app.data.registry

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Config-driven registry for recognized browser packages and OEM system settings packages.
 * Decouples package name matching from interception and strict mode logic.
 */
class PackageRegistry private constructor(
    browsers: Set<String>,
    settings: Set<String>
) {
    private val browserPackages: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(browsers.map { it.trim().lowercase() })
    }

    private val settingsPackages: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply {
        addAll(settings.map { it.trim().lowercase() })
    }

    /**
     * Checks if a package name belongs to a recognized web browser.
     */
    fun isBrowser(packageName: String): Boolean {
        return browserPackages.contains(packageName.trim().lowercase())
    }

    /**
     * Checks if a package name belongs to a system settings, app info, or package installer screen.
     */
    fun isSettingsOrInstaller(packageName: String): Boolean {
        return settingsPackages.contains(packageName.trim().lowercase())
    }

    fun getRecognizedBrowsers(): Set<String> = browserPackages.toSet()

    fun getRecognizedSettingsPackages(): Set<String> = settingsPackages.toSet()

    fun registerBrowser(packageName: String) {
        browserPackages.add(packageName.trim().lowercase())
    }

    fun registerSettingsPackage(packageName: String) {
        settingsPackages.add(packageName.trim().lowercase())
    }

    companion object {
        private const val TAG = "PackageRegistry"

        val DEFAULT_BROWSERS = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "com.brave.browser",
            "com.brave.browser_beta",
            "com.microsoft.emmx",
            "com.microsoft.emmx.beta",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.touch",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser"
        )

        val DEFAULT_SETTINGS = setOf(
            "com.android.settings",
            "com.google.android.settings.intelligence",
            "com.samsung.android.settings",
            "com.miui.securitycenter",
            "com.miui.cleanmaster",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.vivo.permissionmanager",
            "com.iqoo.secure",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller"
        )

        fun createDefault(): PackageRegistry {
            return PackageRegistry(DEFAULT_BROWSERS, DEFAULT_SETTINGS)
        }

        fun fromJson(jsonString: String): PackageRegistry {
            val root = JSONObject(jsonString)
            val browsers = root.optJSONArray("browserPackages")?.toStringSet() ?: emptySet()
            val settings = root.optJSONArray("settingsPackages")?.toStringSet() ?: emptySet()
            return PackageRegistry(browsers, settings)
        }

        fun fromAsset(context: Context, assetFileName: String = "package_registry.json"): PackageRegistry {
            return try {
                context.assets.open(assetFileName).use { inputStream ->
                    val json = inputStream.bufferedReader().use { it.readText() }
                    fromJson(json)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load/parse package registry from asset '$assetFileName'. Falling back to defaults.", e)
                createDefault()
            }
        }

        private fun JSONArray.toStringSet(): Set<String> {
            val set = mutableSetOf<String>()
            for (i in 0 until length()) {
                val item = optString(i, "").trim().lowercase()
                if (item.isNotEmpty()) {
                    set.add(item)
                }
            }
            return set
        }
    }
}
