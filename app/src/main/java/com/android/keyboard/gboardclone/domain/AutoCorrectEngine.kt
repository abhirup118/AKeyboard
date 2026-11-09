package com.android.keyboard.gboardclone.domain

import com.android.keyboard.gboardclone.data.trie.Trie
import com.android.keyboard.gboardclone.data.trie.TrieNode
import kotlin.math.min

/**
 * Classical Dynamic Programming Edit Distance & Auto-Correction Engine.
 *
 * Provides:
 * - Dynamic programming Levenshtein distance computation with transposition support (Damerau-Levenshtein)
 * - Fast Trie-guided DP branch exploration bounded by [maxDistance] <= 2
 * - Frequency-weighted candidate ranking
 *
 * Example:
 *  "teh" -> "the"
 *  "recive" -> "receive"
 */
class AutoCorrectEngine(
    private val trie: Trie,
    private val frequencyMapProvider: () -> Map<String, Int> = { emptyMap() }
) {

    /**
     * Finds spelling corrections for [word] within [maxDistance] (default = 2).
     * Results are ranked by:
     * 1. Edit distance (ascending: distance 1 is preferred over 2)
     * 2. Frequency (descending: more common words preferred)
     * 3. Alphabetical order (ascending)
     */
    fun getCorrections(
        word: String,
        maxDistance: Int = 2,
        limit: Int = 5
    ): List<String> {
        if (word.isBlank() || maxDistance < 0) return emptyList()
        val target = word.lowercase().trim()

        // If the word itself exists in Trie with frequency > 0, return it first
        val exactFreq = trie.getFrequency(target)
        if (exactFreq > 0) {
            return listOf(target)
        }

        // Candidate match: (word, distance, frequency)
        val candidates = mutableListOf<CorrectionCandidate>()

        // Initial DP row: 0, 1, 2, ..., target.length
        val initialRow = IntArray(target.length + 1) { it }

        // Explore Trie branches with DP row pruning
        for ((char, childNode) in trie.root.children) {
            searchTrieBranch(
                node = childNode,
                letter = char,
                target = target,
                prevRow = initialRow,
                currentPrefix = char.toString(),
                maxDistance = maxDistance,
                results = candidates
            )
        }

        // Incorporate learned frequencies from user frequency map if higher
        val runtimeFreqs = frequencyMapProvider()
        val enriched = candidates.map { candidate ->
            val learned = runtimeFreqs[candidate.word] ?: 0
            val bestFreq = maxOf(candidate.frequency, learned)
            candidate.copy(frequency = bestFreq)
        }

        // Sort candidates
        val sorted = enriched.sortedWith(
            compareBy<CorrectionCandidate> { it.distance }
                .thenByDescending { it.frequency }
                .thenBy { it.word }
        )

        return sorted.map { it.word }.distinct().take(limit)
    }

    /**
     * Recursive Trie branch traversal maintaining dynamic programming edit distance row.
     * Prunes branches where every cell in the current row exceeds [maxDistance].
     */
    private fun searchTrieBranch(
        node: TrieNode,
        letter: Char,
        target: String,
        prevRow: IntArray,
        currentPrefix: String,
        maxDistance: Int,
        results: MutableList<CorrectionCandidate>
    ) {
        val n = target.length
        val currentRow = IntArray(n + 1)
        currentRow[0] = prevRow[0] + 1

        var minRowDistance = currentRow[0]

        for (j in 1..n) {
            val cost = if (target[j - 1] == letter) 0 else 1

            // Classical DP Levenshtein operations:
            // insertion, deletion, substitution
            val insertCost = currentRow[j - 1] + 1
            val deleteCost = prevRow[j] + 1
            val replaceCost = prevRow[j - 1] + cost

            var currentCost = min(min(insertCost, deleteCost), replaceCost)

            // Damerau-Levenshtein transposition check:
            // Handles common typos like "teh" -> "the"
            if (j > 1 && currentPrefix.length > 1) {
                val prevLetter = currentPrefix[currentPrefix.length - 2]
                if (target[j - 1] == prevLetter && target[j - 2] == letter) {
                    // Approximation for adjacent swap in DP row
                    currentCost = min(currentCost, prevRow[j - 2] + 1)
                }
            }

            currentRow[j] = currentCost
            if (currentCost < minRowDistance) {
                minRowDistance = currentCost
            }
        }

        // If node is a completed word and its final edit distance <= maxDistance, record it
        val finalDistance = currentRow[n]
        if (node.isWord && finalDistance <= maxDistance) {
            results.add(CorrectionCandidate(currentPrefix, finalDistance, node.frequency))
        }

        // Branch pruning: if every edit distance in this row is > maxDistance, prune branch
        if (minRowDistance <= maxDistance) {
            for ((nextChar, nextNode) in node.children) {
                searchTrieBranch(
                    node = nextNode,
                    letter = nextChar,
                    target = target,
                    prevRow = currentRow,
                    currentPrefix = currentPrefix + nextChar,
                    maxDistance = maxDistance,
                    results = results
                )
            }
        }
    }

    /**
     * Standalone 2D Dynamic Programming Levenshtein Distance implementation.
     * Guaranteed O(M * N) time complexity.
     *
     * @return Minimum operations (insertions, deletions, substitutions) to transform [s1] into [s2].
     */
    fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // Base cases
        if (m == 0) return n
        if (n == 0) return m
        if (s1 == s2) return 0

        // DP table of size (m + 1) x (n + 1)
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            val c1 = s1[i - 1]
            for (j in 1..n) {
                val c2 = s2[j - 1]
                val cost = if (c1 == c2) 0 else 1

                val insert = dp[i][j - 1] + 1
                val delete = dp[i - 1][j] + 1
                val substitute = dp[i - 1][j - 1] + cost

                var minCost = min(insert, min(delete, substitute))

                // Transposition check (Damerau)
                if (i > 1 && j > 1 && c1 == s2[j - 2] && s1[i - 2] == c2) {
                    minCost = min(minCost, dp[i - 2][j - 2] + 1)
                }

                dp[i][j] = minCost
            }
        }

        return dp[m][n]
    }

    private data class CorrectionCandidate(
        val word: String,
        val distance: Int,
        val frequency: Int
    )
}

