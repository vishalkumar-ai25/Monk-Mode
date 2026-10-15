package com.stayfocused.app.domain

import java.net.URLDecoder

sealed class ParsedOmniboxContent {
    object Empty : ParsedOmniboxContent()

    data class SearchQuery(
        val query: String
    ) : ParsedOmniboxContent()

    data class Url(
        val rawText: String,
        val host: String,
        val path: String,
        val fullNormalizedUrl: String,
        val domain: String,
        val searchQuery: String? = null
    ) : ParsedOmniboxContent()
}

/**
 * Deterministic URL and Omnibox content parser with zero Android framework dependencies.
 */
object UrlNormalizer {

    private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")

    /**
     * Parse raw text extracted from a browser's address bar.
     */
    fun parse(rawText: String?): ParsedOmniboxContent {
        if (rawText.isNullOrBlank()) return ParsedOmniboxContent.Empty

        val trimmed = rawText.trim()
        val safeDecoded = safeUrlDecode(trimmed)

        // 1. Explicit http/https scheme
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return parseUrl(trimmed, safeDecoded)
        }

        // 2. If it contains spaces and no slash or obvious dot domain, it is a search query
        if (trimmed.contains(' ') && !trimmed.contains('/')) {
            return ParsedOmniboxContent.SearchQuery(trimmed)
        }

        // 3. Schemeless domain pattern check (e.g. 'reddit.com/r/all' or 'm.youtube.com')
        val firstSlash = trimmed.indexOf('/')
        val hostCandidate = if (firstSlash != -1) trimmed.substring(0, firstSlash) else trimmed

        if (hostCandidate.contains('.') && !hostCandidate.contains(' ') && DOMAIN_REGEX.matches(trimmed)) {
            return parseUrl("https://$trimmed", safeDecoded)
        }

        // 4. Default to search query if no valid domain structure
        return ParsedOmniboxContent.SearchQuery(trimmed)
    }

    private fun parseUrl(fullUrl: String, decodedUrl: String): ParsedOmniboxContent.Url {
        val schemeEnd = fullUrl.indexOf("://")
        val withoutScheme = if (schemeEnd != -1) fullUrl.substring(schemeEnd + 3) else fullUrl

        val pathStart = withoutScheme.indexOf('/')
        val rawHostAndPort = if (pathStart != -1) withoutScheme.substring(0, pathStart) else withoutScheme
        val host = rawHostAndPort.substringBefore(':').lowercase().trim()

        val rawPath = if (pathStart != -1) withoutScheme.substring(pathStart) else "/"
        val path = if (rawPath.isEmpty()) "/" else rawPath

        val domain = extractRegisteredDomain(host)
        val searchQuery = extractSearchQuery(host, path)

        return ParsedOmniboxContent.Url(
            rawText = fullUrl,
            host = host,
            path = path,
            fullNormalizedUrl = fullUrl,
            domain = domain,
            searchQuery = searchQuery
        )
    }

    /**
     * Extracts the base registered domain (e.g. 'reddit.com' from 'www.reddit.com' or 'old.reddit.com').
     */
    fun extractRegisteredDomain(host: String): String {
        val cleanHost = host.lowercase().trimEnd('.')
        val parts = cleanHost.split('.')
        if (parts.size <= 2) return cleanHost

        // Handle common two-part TLDs (e.g. .co.uk, .com.au, .co.in, .org.uk)
        val secondLevelTlds = setOf("co", "com", "org", "net", "edu", "gov", "ac")
        val tld = parts.last()
        val sld = parts[parts.size - 2]

        return if (parts.size >= 3 && secondLevelTlds.contains(sld) && tld.length == 2) {
            "${parts[parts.size - 3]}.$sld.$tld"
        } else {
            "${parts[parts.size - 2]}.$tld"
        }
    }

    /**
     * Extracts search query parameters from search engine URLs (e.g. google.com/search?q=...).
     */
    fun extractSearchQuery(host: String, path: String): String? {
        val isSearchEngine = host.contains("google.") ||
                host.contains("bing.com") ||
                host.contains("duckduckgo.com") ||
                host.contains("yahoo.com") ||
                host.contains("ecosia.org") ||
                host.contains("yandex.")

        if (!isSearchEngine) return null

        val queryStart = path.indexOf('?')
        if (queryStart == -1) return null

        val queryString = path.substring(queryStart + 1)
        val params = queryString.split('&')
        for (param in params) {
            val keyValue = param.split('=')
            if (keyValue.size == 2) {
                val key = keyValue[0].lowercase()
                val value = keyValue[1]
                if (key == "q" || key == "query" || key == "p") {
                    return safeUrlDecode(value).replace('+', ' ').trim()
                }
            }
        }
        return null
    }

    /**
     * Matches a URL path against a blocked path rule with strict delimiter boundary checks.
     * Rule '/r/all' matches '/r/all', '/r/all/', '/r/all?sort=top', '/r/all#top'.
     * Rule '/r/all' does NOT match '/r/all-inclusive' or '/r/allow'.
     */
    fun matchesPath(actualPath: String, rulePath: String): Boolean {
        val normActual = actualPath.substringBefore('?').substringBefore('#').trimEnd('/')
        val normRule = rulePath.substringBefore('?').substringBefore('#').trimEnd('/')

        if (normActual.equals(normRule, ignoreCase = true)) return true

        val rulePrefix = if (normRule.endsWith('/')) normRule else "$normRule/"
        return normActual.startsWith(rulePrefix, ignoreCase = true)
    }

    /**
     * Strips query parameters and fragments from a URL for privacy-safe history persistence.
     */
    fun stripQueryParams(url: String): String {
        return url.substringBefore('?').substringBefore('#')
    }

    /**
     * Safe URL decoding that falls back to the original string if malformed percent escapes occur.
     */
    fun safeUrlDecode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, "UTF-8")
        }.getOrDefault(value)
    }
}
