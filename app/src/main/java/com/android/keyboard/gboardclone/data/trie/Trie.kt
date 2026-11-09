package com.android.keyboard.gboardclone.data.trie

import java.util.PriorityQueue

/**
 * High-performance Trie (Prefix Tree) implementation for fast autocomplete
 * and prefix searches over 50,000+ words.
 *
 * Characteristics:
 * - O(prefix_length) prefix node navigation
 * - Min-heap bounded top-N suggestion retrieval
 * - Thread-safe insertion and frequency updates
 */
class Trie {

    val root = TrieNode()

    // Total unique words stored
    private var totalWords = 0

    val size: Int
        @Synchronized get() = totalWords

    /**
     * Inserts a word with an initial frequency into the Trie.
     * Normalized to lowercase.
     */
    @Synchronized
    fun insert(word: String, frequency: Int = 1) {
        if (word.isBlank()) return
        val normalized = word.lowercase().trim()

        var current = root
        for (char in normalized) {
            current = current.children.getOrPut(char) { TrieNode() }
        }

        if (!current.isWord) {
            current.isWord = true
            totalWords++
        }
        // If word already existed, update frequency to the higher value or add
        if (frequency > current.frequency) {
            current.frequency = frequency
        }
    }

    /**
     * Increments the frequency count for an existing or new word.
     */
    @Synchronized
    fun incrementFrequency(word: String, delta: Int = 1): Int {
        if (word.isBlank()) return 0
        val normalized = word.lowercase().trim()

        var current = root
        for (char in normalized) {
            current = current.children.getOrPut(char) { TrieNode() }
        }

        if (!current.isWord) {
            current.isWord = true
            totalWords++
        }
        current.frequency += delta
        return current.frequency
    }

    /**
     * Returns true if the exact word is present in the Trie.
     */
    @Synchronized
    fun search(word: String): Boolean {
        if (word.isBlank()) return false
        val node = findNode(word.lowercase().trim())
        return node?.isWord == true
    }

    /**
     * Returns the frequency of an exact word, or 0 if not found.
     */
    @Synchronized
    fun getFrequency(word: String): Int {
        if (word.isBlank()) return 0
        val node = findNode(word.lowercase().trim())
        return if (node?.isWord == true) node.frequency else 0
    }

    /**
     * Returns true if there is any word in the Trie that starts with the given prefix.
     */
    @Synchronized
    fun startsWith(prefix: String): Boolean {
        if (prefix.isEmpty()) return true
        return findNode(prefix.lowercase().trim()) != null
    }

    /**
     * Retrieves the top [limit] suggestions for a given [prefix],
     * ranked primarily by frequency (descending) and secondarily by alphabetical order (ascending).
     *
     * Complexity:
     * - O(prefix.length) to locate prefix node
     * - Fast DFS collecting candidates using a min-heap of size [limit]
     * Execution time is typically < 2ms.
     */
    @Synchronized
    fun getSuggestions(prefix: String, limit: Int = 3): List<Pair<String, Int>> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val normalized = prefix.lowercase().trim()

        val prefixNode = findNode(normalized) ?: return emptyList()

        // Min-heap to maintain top `limit` elements.
        // Lowest frequency at head of heap so we can evict it when larger found.
        val comparator = Comparator<Pair<String, Int>> { a, b ->
            if (a.second != b.second) {
                a.second.compareTo(b.second) // lowest frequency at top of heap
            } else {
                b.first.compareTo(a.first)  // lexicographically larger at top
            }
        }
        val minHeap = PriorityQueue(limit + 1, comparator)

        // Traversal buffer
        val wordBuffer = StringBuilder(normalized)

        // If prefix itself is a valid word, consider it
        if (prefixNode.isWord) {
            minHeap.offer(Pair(wordBuffer.toString(), prefixNode.frequency))
        }

        // DFS into children
        dfsCollect(prefixNode, wordBuffer, minHeap, limit)

        // Drain heap to list and sort by descending frequency, then ascending alphabetical
        val result = mutableListOf<Pair<String, Int>>()
        while (minHeap.isNotEmpty()) {
            minHeap.poll()?.let { result.add(it) }
        }
        result.sortWith { a, b ->
            if (b.second != a.second) {
                b.second.compareTo(a.second) // highest frequency first
            } else {
                a.first.compareTo(b.first)  // alphabetical order
            }
        }

        return result
    }

    /**
     * Navigates from root down to the node corresponding to [prefix].
     */
    private fun findNode(prefix: String): TrieNode? {
        var current = root
        for (char in prefix) {
            current = current.children[char] ?: return null
        }
        return current
    }

    /**
     * Depth-first search to explore subtree below [node].
     */
    private fun dfsCollect(
        node: TrieNode,
        buffer: StringBuilder,
        minHeap: PriorityQueue<Pair<String, Int>>,
        limit: Int
    ) {
        for ((char, childNode) in node.children) {
            buffer.append(char)
            if (childNode.isWord) {
                minHeap.offer(Pair(buffer.toString(), childNode.frequency))
                if (minHeap.size > limit) {
                    minHeap.poll() // Keep heap size bounded to `limit`
                }
            }
            dfsCollect(childNode, buffer, minHeap, limit)
            buffer.deleteCharAt(buffer.length - 1)
        }
    }
}
