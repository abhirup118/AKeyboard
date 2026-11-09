package com.android.keyboard.gboardclone

import com.android.keyboard.gboardclone.data.trie.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrieTest {

    private lateinit var trie: Trie

    @Before
    fun setUp() {
        trie = Trie()
    }

    @Test
    fun `test basic insert and search`() {
        trie.insert("hello", 150)
        trie.insert("world", 100)

        assertTrue(trie.search("hello"))
        assertTrue(trie.search("HELLO"))
        assertTrue(trie.search("world"))
        assertFalse(trie.search("hell"))
        assertFalse(trie.search("unknown"))
    }

    @Test
    fun `test startsWith`() {
        trie.insert("hello", 150)
        trie.insert("help", 40)

        assertTrue(trie.startsWith("hel"))
        assertTrue(trie.startsWith("hell"))
        assertTrue(trie.startsWith("help"))
        assertTrue(trie.startsWith("h"))
        assertFalse(trie.startsWith("hex"))
    }

    @Test
    fun `test prefix suggestions ranked by frequency and alphabetical`() {
        trie.insert("hello", 150)
        trie.insert("help", 40)
        trie.insert("held", 10)
        trie.insert("helmet", 25)
        trie.insert("helicopter", 5)

        val suggestions = trie.getSuggestions("hel", limit = 4).map { it.first }

        // Expected order:
        // hello (150)
        // help (40)
        // helmet (25)
        // held (10)
        assertEquals(listOf("hello", "help", "helmet", "held"), suggestions)
    }

    @Test
    fun `test tie breaker alphabetical order`() {
        trie.insert("apple", 50)
        trie.insert("apply", 50)

        val suggestions = trie.getSuggestions("app", limit = 2).map { it.first }
        assertEquals(listOf("apple", "apply"), suggestions)
    }

    @Test
    fun `test frequency increment`() {
        trie.insert("gboard", 10)
        val newFreq = trie.incrementFrequency("gboard", 15)
        assertEquals(25, newFreq)
        assertEquals(25, trie.getFrequency("gboard"))
    }

    @Test
    fun `test performance under 20ms with thousands of words`() {
        for (i in 1..10000) {
            trie.insert("word$i", i % 100)
        }

        val startTime = System.currentTimeMillis()
        val suggestions = trie.getSuggestions("word1", limit = 3)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Expected retrieval time < 20ms, took ${elapsed}ms", elapsed < 20)
        assertTrue(suggestions.isNotEmpty())
    }
}

