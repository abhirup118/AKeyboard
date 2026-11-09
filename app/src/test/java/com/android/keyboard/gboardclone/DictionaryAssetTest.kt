package com.android.keyboard.gboardclone

import com.android.keyboard.gboardclone.data.trie.Trie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Validates the REAL bundled dictionary asset (not hand-inserted words),
 * guarding against regressions like a corpus that only covers a few letters.
 */
class DictionaryAssetTest {

    companion object {
        private val trie = Trie()

        @BeforeClass
        @JvmStatic
        fun loadAsset() {
            // Unit tests run with working dir = module dir (app/)
            val file = File("src/main/assets/dictionary.txt")
            file.forEachLine { line ->
                val t = line.trim()
                if (t.isEmpty() || t.startsWith("#")) return@forEachLine
                val parts = t.split(" ")
                trie.insert(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: 1)
            }
        }
    }

    @Test
    fun `dictionary has at least 50k words`() {
        assertTrue("size=${trie.size}", trie.size >= 50_000)
    }

    @Test
    fun `every letter a to z has words`() {
        for (c in 'a'..'z') {
            assertTrue("No words start with '$c'", trie.startsWith(c.toString()))
        }
    }

    @Test
    fun `hel suggests hello help held`() {
        val top = trie.getSuggestions("hel", 10).map { it.first }
        assertTrue(top.toString(), top.containsAll(listOf("hello", "help", "held")))
        // Real corpus frequencies decide the order; just verify it is frequency-sorted.
        val freqs = top.map { trie.getFrequency(it) }
        assertEquals(freqs.sortedDescending(), freqs)
    }
}
