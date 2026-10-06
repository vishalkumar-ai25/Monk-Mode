package com.stayfocused.app.domain

/**
 * Pure, framework-free classifier that decides whether an accessibility window event
 * originates from a transient system overlay (notification shade, volume panel, IME,
 * permission dialog, own overlay) rather than a genuine foreground app switch.
 *
 * Transient events must NOT:
 *  - trigger overlay hide on the currently-blocked app,
 *  - update `lastForegroundPackage`, or
 *  - record an app launch.
 *
 * @param imePackagesProvider  Injected lambda that returns the set of currently-enabled
 *   IME packages.  In production this is resolved from
 *   `InputMethodManager.enabledInputMethodList`; in tests a fixed set is passed directly.
 * @param selfPackage  The application's own package name (supply `BuildConfig.APPLICATION_ID`
 *   from the call-site).  Its overlay windows are transient.
 * @param imeCacheTtlMs  How long (ms) the resolved IME package set is considered fresh before
 *   the provider is called again.  Defaults to 500 ms.  Pass `0L` in tests that need
 *   immediate re-evaluation.
 */
class TransientWindowFilter(
    private val imePackagesProvider: () -> Set<String>,
    val selfPackage: String,
    private val imeCacheTtlMs: Long = 500L
) {

    companion object {
        /**
         * System UI packages that produce window events for non-app overlays
         * (notification shade, volume panel, recents, quick-settings, etc.).
         */
        val SYSTEM_UI_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.systemui",  // Samsung OneUI
            "com.miui.systemui",             // MIUI / Xiaomi
            "com.oppo.systemui",             // OPPO / Realme
            "com.oplus.systemui",            // OnePlus (OxygenOS 12+)
            "com.vivo.systemui",             // Vivo Funtouch / OriginOS
            "com.hihonor.systemui",          // Honor MagicOS
            "com.asus.systemui",             // ASUS ZenUI
            "com.nothing.systemui"           // Nothing OS
        )

        /**
         * Known permission-controller package names.
         * Checked by package identity (fast path, check 4).
         */
        private val PERMISSION_CONTROLLER_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.samsung.android.permissioncontroller"
        )

        /**
         * Class-name fragments used to catch OEM-renamed permission dialogs that live
         * in packages not listed in [PERMISSION_CONTROLLER_PACKAGES].
         * Pre-compiled as a single alternation Regex for hot-path efficiency.
         */
        private val PERMISSION_DIALOG_CLASS_REGEX = Regex(
            "GrantPermissionsActivity|ReviewPermissionsActivity",
            RegexOption.IGNORE_CASE
        )
    }

    // ── IME package cache ─────────────────────────────────────────────────────
    // The IME provider can call into a system service (IPC) which allocates on
    // every invocation.  We throttle calls with a monotonic System.nanoTime() TTL
    // (default 500 ms) so burst window-events during typing do not hammer the system service,
    // and device clock changes/NTP syncs never cause TTL starvation.
    @Volatile private var cachedImePackages: Set<String> = emptySet()
    @Volatile private var lastImeRefreshNs: Long = 0L

    private fun resolvedImePackages(): Set<String> {
        val nowNs = System.nanoTime()
        val ttlNs = imeCacheTtlMs * 1_000_000L
        if (imeCacheTtlMs <= 0L || nowNs - lastImeRefreshNs >= ttlNs) {
            cachedImePackages = try {
                imePackagesProvider()
            } catch (_: Exception) {
                emptySet()
            }
            lastImeRefreshNs = nowNs
        }
        return cachedImePackages
    }

    fun isIme(pkg: String): Boolean = pkg in resolvedImePackages()

    /**
     * Returns `true` when the [pkg]/[className] pair represents a transient system
     * overlay that should be ignored for foreground-tracking and overlay-hide purposes.
     *
     * @param pkg       Package name from the accessibility event.
     * @param className Fully-qualified class name from the accessibility event, or `null`.
     */
    fun isTransient(pkg: String, className: String?): Boolean {
        // 1. Own overlay / UI windows are transient
        if (pkg.equals(selfPackage, ignoreCase = true)) return true

        // 2. System UI overlays (notification shade, volume, recents, quick-settings)
        if (pkg in SYSTEM_UI_PACKAGES) return true

        // 3. Input-method (keyboard) packages — resolved via injected lambda, monotonic TTL-cached
        if (pkg in resolvedImePackages()) return true

        // 4. Known permission-controller packages (fast exact-match path)
        if (pkg in PERMISSION_CONTROLLER_PACKAGES) return true

        // 5. Class-name heuristic for OEM-renamed permission dialogs
        if (className != null && PERMISSION_DIALOG_CLASS_REGEX.containsMatchIn(className)) return true

        return false
    }
}
