package com.android.keyboard.gboardclone

import com.android.keyboard.gboardclone.data.trie.Trie
import com.android.keyboard.gboardclone.domain.AutoCorrectEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoCorrectEngineTest {

    private lateinit var trie: Trie
    private lateinit var engine: AutoCorrectEngine

    @Before
    fun setUp() {
        trie = Trie()
        // Pre-populate with typical words and frequencies
        trie.insert("the", 1000)
        trie.insert("receive", 300)
        trie.insert("hello", 150)
        trie.insert("help", 40)
        trie.insert("held", 10)
        trie.insert("helmet", 25)

        engine = AutoCorrectEngine(trie)
    }

    @Test
    fun `test direct Levenshtein DP computation`() {
        // Exact equality
        assertEquals(0, engine.computeLevenshteinDistance("word", "word"))

        // Single insertion / deletion
        assertEquals(1, engine.computeLevenshteinDistance("recive", "receive"))
        assertEquals(1, engine.computeLevenshteinDistance("cat", "cats"))

        // Single substitution
        assertEquals(1, engine.computeLevenshteinDistance("cat", "cot"))

        // Transposition (Damerau)
        assertEquals(1, engine.computeLevenshteinDistance("teh", "the"))

        // Distance 2 (e.g. "cat" -> "dog" is 3, "cat" -> "car" is 1, "book" -> "back" is 2)
        assertEquals(2, engine.computeLevenshteinDistance("book", "back"))
        assertEquals(3, engine.computeLevenshteinDistance("kitten", "sitting"))
    }

    @Test
    fun `test typo correction teh to the`() {
        val corrections = engine.getCorrections("teh", maxDistance = 2)
        assertTrue("Expected 'the' in corrections for 'teh', got $corrections", corrections.contains("the"))
        assertEquals("the", corrections.first())
    }

    @Test
    fun `test typo correction recive to receive`() {
        val corrections = engine.getCorrections("recive", maxDistance = 2)
        assertTrue("Expected 'receive' in corrections for 'recive', got $corrections", corrections.contains("receive"))
        assertEquals("receive", corrections.first())
    }

    @Test
    fun `test corrections ranked by distance then frequency`() {
        // Suppose we have "hell" with freq 20 and "help" with freq 40
        trie.insert("hell", 20)

        // For typo "helo":
        // "hell" has distance 1 (replace o with l)
        // "hello" has distance 1 (insert l)
        // "hello" frequency is 150, "hell" is 20
        val corrections = engine.getCorrections("helo", maxDistance = 2)
        assertEquals("hello", corrections.first())
    }
}
