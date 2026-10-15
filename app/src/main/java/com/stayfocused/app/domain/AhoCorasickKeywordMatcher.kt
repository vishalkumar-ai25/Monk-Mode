package com.stayfocused.app.domain

import java.util.ArrayDeque

enum class KeywordCategory {
    ADULT,
    CUSTOM,
    PATH
}

data class KeywordMatch(
    val keyword: String,
    val category: KeywordCategory,
    val startIndex: Int,
    val endIndex: Int
)

/**
 * High-performance, linear-time O(N + M) Aho-Corasick Trie Automaton.
 * Built-in delimiter boundary verification eliminates the Scunthorpe Catastrophe
 * (e.g., ensuring 'ass' never false-matches 'class', 'pass', 'assets', or 'passwords.google.com').
 */
class AhoCorasickKeywordMatcher private constructor(
    private val root: TrieNode
) {

    private class TrieNode {
        val children: MutableMap<Char, TrieNode> = HashMap()
        var fail: TrieNode? = null
        val outputs: MutableList<Pair<String, KeywordCategory>> = ArrayList()
    }

    /**
     * Finds the first valid keyword match that satisfies delimiter boundary conditions.
     */
    fun findFirstMatch(text: String?): KeywordMatch? {
        if (text.isNullOrEmpty()) return null
        val lower = text.lowercase()

        var current = root
        for (i in lower.indices) {
            val c = lower[i]

            while (current !== root && !current.children.containsKey(c)) {
                current = current.fail ?: root
            }
            current = current.children[c] ?: root

            for (output in current.outputs) {
                val keyword = output.first
                val category = output.second
                val startIndex = i - keyword.length + 1
                val endIndex = i + 1

                if (isWordBoundarySatisfied(lower, startIndex, endIndex, category)) {
                    return KeywordMatch(
                        keyword = keyword,
                        category = category,
                        startIndex = startIndex,
                        endIndex = endIndex
                    )
                }
            }
        }
        return null
    }

    /**
     * Finds all valid keyword matches in the input string.
     */
    fun findAllMatches(text: String?): List<KeywordMatch> {
        if (text.isNullOrEmpty()) return emptyList()
        val lower = text.lowercase()
        val results = mutableListOf<KeywordMatch>()

        var current = root
        for (i in lower.indices) {
            val c = lower[i]

            while (current !== root && !current.children.containsKey(c)) {
                current = current.fail ?: root
            }
            current = current.children[c] ?: root

            for (output in current.outputs) {
                val keyword = output.first
                val category = output.second
                val startIndex = i - keyword.length + 1
                val endIndex = i + 1

                if (isWordBoundarySatisfied(lower, startIndex, endIndex, category)) {
                    results.add(
                        KeywordMatch(
                            keyword = keyword,
                            category = category,
                            startIndex = startIndex,
                            endIndex = endIndex
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Checks if the text contains any prohibited keyword or path.
     */
    fun isBlocked(text: String?): Boolean = findFirstMatch(text) != null

    companion object {

        private val KEYWORD_DELIMITERS = setOf(
            ' ', '/', '?', '&', '=', '+', '%', '.', '_', '-', '#', ':', ';', '~', '\\',
            '(', ')', '[', ']', '{', '}', ',', '@', '!', '\'', '"', '<', '>', '\t', '\n'
        )

        private val PATH_DELIMITERS = setOf(
            '/', '?', '&', '#', ' ', '\t', '\n'
        )

        fun isDelimiter(c: Char): Boolean = KEYWORD_DELIMITERS.contains(c) || !c.isLetterOrDigit()

        fun isPathDelimiter(c: Char): Boolean = PATH_DELIMITERS.contains(c)

        /**
         * Verifies that the matched span [startIndex, endIndex) is bounded by appropriate delimiters,
         * preventing substring collisions (e.g. 'ass' in 'class', '/r/all' in '/r/all-inclusive').
         */
        fun isWordBoundarySatisfied(
            text: String,
            startIndex: Int,
            endIndex: Int,
            category: KeywordCategory
        ): Boolean {
            return if (category == KeywordCategory.PATH) {
                // For paths: the leading slash is itself the delimiter, or must follow a path delimiter
                val leftOk = startIndex == 0 || text[startIndex] == '/' || isPathDelimiter(text[startIndex - 1])
                // Right boundary must be end-of-string or followed by a path delimiter ('/', '?', '#', etc.)
                val rightOk = endIndex == text.length || isPathDelimiter(text[endIndex])
                leftOk && rightOk
            } else {
                // For keywords: must be isolated by general word delimiters
                val leftOk = startIndex == 0 || isDelimiter(text[startIndex - 1])
                val rightOk = endIndex == text.length || isDelimiter(text[endIndex])
                leftOk && rightOk
            }
        }

        /**
         * Curated list of adult/NSFW explicit tokens.
         */
        val DEFAULT_ADULT_KEYWORDS: List<String> = listOf(
            "porn", "porno", "pornography", "xxx", "nsfw", "hentai",
            "xhamster", "pornhub", "xvideos", "redtube", "youporn", "brazzers",
            "chaturbate", "onlyfans", "erotic", "erotica", "nudity", "sex",
            "boobs", "camgirl", "stripchat", "rule34", "milf", "bdsm",
            "fetish", "taboo", "incest", "playboy", "nude", "dildo",
            "masturbate", "masturbation", "masturbating", "vagina", "penis", "blowjob", "creampie", "hardcore",
            "softcore", "gangbang", "cuckold", "threesome", "escort", "escorts"
        )

        /**
         * Compiles an in-memory Aho-Corasick automaton.
         */
        fun create(
            customKeywords: List<String> = emptyList(),
            blockedPaths: List<String> = emptyList(),
            includeAdultDictionary: Boolean = true
        ): AhoCorasickKeywordMatcher {
            val root = TrieNode()

            // 1. Insert adult dictionary
            if (includeAdultDictionary) {
                for (kw in DEFAULT_ADULT_KEYWORDS.distinct()) {
                    val clean = kw.trim().lowercase()
                    if (clean.isNotEmpty()) {
                        insertPattern(root, clean, KeywordCategory.ADULT)
                    }
                }
            }

            // 2. Insert custom keywords
            for (kw in customKeywords.distinct()) {
                val clean = kw.trim().lowercase()
                if (clean.isNotEmpty()) {
                    insertPattern(root, clean, KeywordCategory.CUSTOM)
                }
            }

            // 3. Insert blocked URL paths
            for (path in blockedPaths.distinct()) {
                val clean = path.trim().lowercase()
                if (clean.isNotEmpty()) {
                    insertPattern(root, clean, KeywordCategory.PATH)
                }
            }

            // 4. Build Aho-Corasick BFS failure links
            buildFailureLinks(root)

            return AhoCorasickKeywordMatcher(root)
        }

        private fun insertPattern(root: TrieNode, pattern: String, category: KeywordCategory) {
            var current = root
            for (c in pattern) {
                current = current.children.computeIfAbsent(c) { TrieNode() }
            }
            current.outputs.add(pattern to category)
        }

        private fun buildFailureLinks(root: TrieNode) {
            val queue = ArrayDeque<TrieNode>()

            // Depth 1 nodes fail to root
            for (child in root.children.values) {
                child.fail = root
                queue.add(child)
            }

            // BFS for deeper nodes
            while (!queue.isEmpty()) {
                val current = queue.poll() ?: break

                for ((char, child) in current.children) {
                    var fallback = current.fail
                    while (fallback !== null && !fallback.children.containsKey(char)) {
                        fallback = fallback.fail
                    }
                    val targetFail = fallback?.children?.get(char) ?: root
                    child.fail = targetFail
                    child.outputs.addAll(targetFail.outputs)
                    queue.add(child)
                }
            }
        }
    }
}
