package com.android.keyboard.gboardclone.data.trie

/**
 * Node in the Trie data structure.
 *
 * Each node maintains:
 * - [children]: map of character to next TrieNode
 * - [isWord]: boolean flag indicating end of a valid word
 * - [frequency]: frequency weight (dictionary base + learned occurrences)
 */
class TrieNode {
    val children = HashMap<Char, TrieNode>()
    var isWord: Boolean = false
    var frequency: Int = 0
}

