package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.BlockReason

/**
 * Extracted view context from a browser window.
 * Contains only address bar text and window attributes.
 * Zero user web page body content or credentials.
 */
data class BrowserAddressBarViewContext(
    val packageName: String,
    val addressBarText: String?,
    val isFocused: Boolean = false,
    val isFocusable: Boolean = true,
    val isIncognito: Boolean = false
)

sealed class BrowserInspectionResult {
    object IgnoredNotBrowser : BrowserInspectionResult()
    object NoAddressBarFound : BrowserInspectionResult()
    object TypingInOmnibox : BrowserInspectionResult()
    object EmptyContent : BrowserInspectionResult()

    data class Allowed(
        val parsed: ParsedOmniboxContent,
        val isIncognito: Boolean
    ) : BrowserInspectionResult()

    data class Blocked(
        val target: String,
        val reason: BlockReason.WebBlocked,
        val isIncognito: Boolean
    ) : BrowserInspectionResult()
}

/**
 * Pure Kotlin domain evaluator for browser address bars.
 * Enforces domain, path, and keyword rules with 100% zero screen scraping.
 */
class BrowserAddressBarInspector(
    private val ahoCorasickMatcherProvider: () -> AhoCorasickKeywordMatcher,
    private val blockedDomainsProvider: () -> Set<String>,
    private val blockedPathsProvider: () -> List<String>
) {

    fun inspect(context: BrowserAddressBarViewContext): BrowserInspectionResult {
        if (!BrowserUrlBarRegistry.isSupportedBrowser(context.packageName)) {
            return BrowserInspectionResult.IgnoredNotBrowser
        }

        val rawText = context.addressBarText
        if (rawText == null) {
            return BrowserInspectionResult.NoAddressBarFound
        }

        // Omnibox Typing Guard: while actively typing, defer until navigation commits
        if (context.isFocused && context.isFocusable) {
            return BrowserInspectionResult.TypingInOmnibox
        }

        val parsed = UrlNormalizer.parse(rawText)
        if (parsed is ParsedOmniboxContent.Empty) {
            return BrowserInspectionResult.EmptyContent
        }

        val blockedDomains = blockedDomainsProvider()
        val blockedPaths = blockedPathsProvider()
        val matcher = ahoCorasickMatcherProvider()

        when (parsed) {
            is ParsedOmniboxContent.Url -> {
                // 1. Domain Block Check
                if (blockedDomains.contains(parsed.domain) || blockedDomains.contains(parsed.host)) {
                    return BrowserInspectionResult.Blocked(
                        target = parsed.domain,
                        reason = BlockReason.WebBlocked(
                            target = parsed.domain,
                            matchType = "DOMAIN",
                            matchedRule = parsed.domain
                        ),
                        isIncognito = context.isIncognito
                    )
                }

                // 2. URL Path Block Check
                for (rulePath in blockedPaths) {
                    val hasHost = rulePath.contains('/') && !rulePath.startsWith('/') && rulePath.substringBefore('/').contains('.')
                    val isMatch = if (hasHost) {
                        // Rule has host and path (e.g. 'reddit.com/r/all')
                        val ruleHost = rulePath.substringBefore('/').lowercase()
                        val subPath = "/" + rulePath.substringAfter('/')
                        val isHostMatch = parsed.host.equals(ruleHost, ignoreCase = true) ||
                                parsed.host.endsWith(".$ruleHost", ignoreCase = true) ||
                                parsed.domain.equals(ruleHost, ignoreCase = true)
                        isHostMatch && UrlNormalizer.matchesPath(parsed.path, subPath)
                    } else {
                        // Relative path rule (e.g. '/r/all' or 'r/all')
                        val normPath = if (rulePath.startsWith('/')) rulePath else "/$rulePath"
                        UrlNormalizer.matchesPath(parsed.path, normPath)
                    }

                    if (isMatch) {
                        return BrowserInspectionResult.Blocked(
                            target = parsed.fullNormalizedUrl,
                            reason = BlockReason.WebBlocked(
                                target = parsed.fullNormalizedUrl,
                                matchType = "PATH",
                                matchedRule = rulePath
                            ),
                            isIncognito = context.isIncognito
                        )
                    }
                }

                // 3. Keyword / Adult Check on Search Query or Full URL (percent-decoded)
                val rawTarget = parsed.searchQuery ?: parsed.fullNormalizedUrl
                val queryToScan = UrlNormalizer.safeUrlDecode(rawTarget)
                val match = matcher.findFirstMatch(queryToScan)
                if (match != null) {
                    return BrowserInspectionResult.Blocked(
                        target = match.keyword,
                        reason = BlockReason.WebBlocked(
                            target = queryToScan,
                            matchType = match.category.name,
                            matchedRule = match.keyword
                        ),
                        isIncognito = context.isIncognito
                    )
                }

                return BrowserInspectionResult.Allowed(parsed, context.isIncognito)
            }

            is ParsedOmniboxContent.SearchQuery -> {
                // Scan search query for keywords and adult tokens (percent-decoded)
                val queryToScan = UrlNormalizer.safeUrlDecode(parsed.query)
                val match = matcher.findFirstMatch(queryToScan)
                if (match != null) {
                    return BrowserInspectionResult.Blocked(
                        target = match.keyword,
                        reason = BlockReason.WebBlocked(
                            target = queryToScan,
                            matchType = match.category.name,
                            matchedRule = match.keyword
                        ),
                        isIncognito = context.isIncognito
                    )
                }

                return BrowserInspectionResult.Allowed(parsed, context.isIncognito)
            }

            is ParsedOmniboxContent.Empty -> {
                return BrowserInspectionResult.EmptyContent
            }
        }
    }
}
