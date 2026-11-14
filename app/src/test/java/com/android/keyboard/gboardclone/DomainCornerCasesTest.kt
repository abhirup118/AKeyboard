package com.android.keyboard.gboardclone

import com.android.keyboard.gboardclone.data.db.LearnedWordDao
import com.android.keyboard.gboardclone.data.db.LearnedWordEntity
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.data.trie.Trie
import com.android.keyboard.gboardclone.domain.AutoCorrectEngine
import com.android.keyboard.gboardclone.domain.AutocompleteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exhaustive unit tests for edge and corner cases in:
 * 1. domain/AutoCorrectEngine.kt
 * 2. domain/AutocompleteEngine.kt
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DomainCornerCasesTest {

    private lateinit var trie: Trie
    private lateinit var autoCorrectEngine: AutoCorrectEngine
    private lateinit var fakeDao: FakeLearnedWordDao
    private lateinit var suggestionRepository: SuggestionRepository
    private lateinit var autocompleteEngine: AutocompleteEngine
    private val testDispatcher = StandardTestDispatcher()
    private val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setUp() {
        trie = Trie()
        fakeDao = FakeLearnedWordDao()
        suggestionRepository = SuggestionRepository(trie, fakeDao, repoScope, testDispatcher)
        autoCorrectEngine = AutoCorrectEngine(trie)
        autocompleteEngine = AutocompleteEngine(suggestionRepository)
    }

    // =========================================================================
    // 1. AutoCorrectEngine: Empty string, whitespace-only, single character
    // =========================================================================

    @Test
    fun `autoCorrect - empty string returns empty list`() {
        val result = autoCorrectEngine.getCorrections("")
        assertTrue("Expected empty list for empty string", result.isEmpty())
    }

    @Test
    fun `autoCorrect - whitespace only strings return empty list`() {
        assertTrue(autoCorrectEngine.getCorrections(" ").isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("    ").isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("\t\n\r").isEmpty())
    }

    @Test
    fun `autoCorrect - single character existing in Trie returns exact match`() {
        trie.insert("a", 100)
        trie.insert("i", 90)

        val result = autoCorrectEngine.getCorrections("a")
        assertEquals(listOf("a"), result)
    }

    @Test
    fun `autoCorrect - single character not in Trie finds close single or two letter words`() {
        trie.insert("an", 50)
        trie.insert("at", 40)
        trie.insert("in", 30)

        // Target "a" has distance 1 to "an" and "at" (insertion)
        val result = autoCorrectEngine.getCorrections("a", maxDistance = 1)
        assertTrue("Expected 'an' in corrections", result.contains("an"))
        assertTrue("Expected 'at' in corrections", result.contains("at"))
        assertFalse("Did not expect 'in' with maxDistance 1", result.contains("in"))
    }

    @Test
    fun `autoCorrect - single character with only long words in Trie returns empty list`() {
        trie.insert("elephant", 100)
        trie.insert("keyboard", 200)

        val result = autoCorrectEngine.getCorrections("z", maxDistance = 2)
        assertTrue("Expected empty list when no close words exist", result.isEmpty())
    }

    // =========================================================================
    // 2. AutoCorrectEngine: Exact match lookup
    // =========================================================================

    @Test
    fun `autoCorrect - exact match lookup returns immediately with that word`() {
        trie.insert("hello", 100)
        trie.insert("help", 500)
        trie.insert("hell", 300)

        val result = autoCorrectEngine.getCorrections("hello", maxDistance = 2, limit = 5)
        assertEquals("Exact match should return only the target word immediately", listOf("hello"), result)
    }

    @Test
    fun `autoCorrect - exact match handles mixed case and whitespace trimming`() {
        trie.insert("keyboard", 250)

        val result = autoCorrectEngine.getCorrections("  KeyBoard  ")
        assertEquals(listOf("keyboard"), result)
    }

    @Test
    fun `autoCorrect - uppercase and mixed case input normalization for typos`() {
        trie.insert("the", 1000)

        val upperResult = autoCorrectEngine.getCorrections("TEH", maxDistance = 1)
        assertTrue("Expected 'the' for 'TEH'", upperResult.contains("the"))

        val mixedResult = autoCorrectEngine.getCorrections("  tEh  ", maxDistance = 1)
        assertTrue("Expected 'the' for '  tEh  '", mixedResult.contains("the"))
    }

    // =========================================================================
    // 3. AutoCorrectEngine: Negative maxDistance
    // =========================================================================

    @Test
    fun `autoCorrect - negative maxDistance returns empty list gracefully`() {
        trie.insert("cat", 100)

        assertTrue(autoCorrectEngine.getCorrections("cat", maxDistance = -1).isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("cxt", maxDistance = -1).isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("cat", maxDistance = -10).isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("cat", maxDistance = Int.MIN_VALUE).isEmpty())
    }

    // =========================================================================
    // 4. AutoCorrectEngine: maxDistance = 0 (exact match only)
    // =========================================================================

    @Test
    fun `autoCorrect - maxDistance 0 returns exact match when word exists`() {
        trie.insert("cat", 100)

        val result = autoCorrectEngine.getCorrections("cat", maxDistance = 0)
        assertEquals(listOf("cat"), result)
    }

    @Test
    fun `autoCorrect - maxDistance 0 returns empty list for typos`() {
        trie.insert("cat", 100)

        val result = autoCorrectEngine.getCorrections("cot", maxDistance = 0)
        assertTrue("Typo with maxDistance=0 must return empty list", result.isEmpty())
    }

    // =========================================================================
    // 5. AutoCorrectEngine: maxDistance = 1 vs maxDistance = 2
    // =========================================================================

    @Test
    fun `autoCorrect - maxDistance 1 vs maxDistance 2 candidate filtering`() {
        trie.insert("cat", 100)   // dist 1 from "caat" (1 deletion)
        trie.insert("cot", 50)    // dist 2 from "caat" (1 deletion + 1 substitution)
        trie.insert("dog", 10)    // dist > 2

        val dist1Results = autoCorrectEngine.getCorrections("caat", maxDistance = 1)
        assertEquals("maxDistance=1 should only include distance 1 candidate", listOf("cat"), dist1Results)

        val dist2Results = autoCorrectEngine.getCorrections("caat", maxDistance = 2)
        assertTrue("maxDistance=2 must include 'cat'", dist2Results.contains("cat"))
        assertTrue("maxDistance=2 must include 'cot'", dist2Results.contains("cot"))
        assertFalse("maxDistance=2 must not include 'dog'", dist2Results.contains("dog"))
    }

    @Test
    fun `autoCorrect - word requiring 2 edits returns empty on maxDistance 1 but succeeds on 2`() {
        trie.insert("cat", 100)

        // "catty" -> "cat" requires 2 deletions ('t', 'y')
        val dist1 = autoCorrectEngine.getCorrections("catty", maxDistance = 1)
        assertTrue("Distance 2 word must not be found with maxDistance=1", dist1.isEmpty())

        val dist2 = autoCorrectEngine.getCorrections("catty", maxDistance = 2)
        assertEquals(listOf("cat"), dist2)
    }

    // =========================================================================
    // 6. AutoCorrectEngine: Transposition at start, middle, and end
    // =========================================================================

    @Test
    fun `autoCorrect - target word with transposition at start`() {
        trie.insert("star", 200)
        trie.insert("the", 500)

        // "tsar" -> "star" (swap 't' and 's' at indices 0 and 1)
        val tsarCorrections = autoCorrectEngine.getCorrections("tsar", maxDistance = 1)
        assertTrue("Expected 'star' for typo 'tsar'", tsarCorrections.contains("star"))

        // "hte" -> "the" (swap 'h' and 't' at indices 0 and 1)
        val hteCorrections = autoCorrectEngine.getCorrections("hte", maxDistance = 1)
        assertTrue("Expected 'the' for typo 'hte'", hteCorrections.contains("the"))
    }

    @Test
    fun `autoCorrect - target word with transposition in middle`() {
        trie.insert("what", 300)
        trie.insert("clean", 150)

        // "waht" -> "what" (swap 'a' and 'h' in middle)
        val wahtCorrections = autoCorrectEngine.getCorrections("waht", maxDistance = 1)
        assertTrue("Expected 'what' for typo 'waht'", wahtCorrections.contains("what"))

        // "claen" -> "clean" (swap 'a' and 'e' in middle)
        val claenCorrections = autoCorrectEngine.getCorrections("claen", maxDistance = 1)
        assertTrue("Expected 'clean' for typo 'claen'", claenCorrections.contains("clean"))
    }

    @Test
    fun `autoCorrect - target word with transposition at end`() {
        trie.insert("the", 500)
        trie.insert("form", 200)
        trie.insert("hello", 300)

        // "teh" -> "the" (swap 'e' and 'h' at end)
        val tehCorrections = autoCorrectEngine.getCorrections("teh", maxDistance = 1)
        assertTrue("Expected 'the' for typo 'teh'", tehCorrections.contains("the"))

        // "fomr" -> "form" (swap 'm' and 'r' at end)
        val fomrCorrections = autoCorrectEngine.getCorrections("fomr", maxDistance = 1)
        assertTrue("Expected 'form' for typo 'fomr'", fomrCorrections.contains("form"))

        // "helol" -> "hello" (swap 'o' and 'l' at end)
        val helolCorrections = autoCorrectEngine.getCorrections("helol", maxDistance = 1)
        assertTrue("Expected 'hello' for typo 'helol'", helolCorrections.contains("hello"))
    }

    // =========================================================================
    // 7. AutoCorrectEngine: Target word with substitution only
    // =========================================================================

    @Test
    fun `autoCorrect - target word with substitution only`() {
        trie.insert("cat", 100)
        trie.insert("bat", 50)

        // "cxt" has 'x' instead of 'a'
        val result = autoCorrectEngine.getCorrections("cxt", maxDistance = 1)
        assertTrue("Expected 'cat' for 'cxt'", result.contains("cat"))
        assertEquals("cat", result.first())
    }

    // =========================================================================
    // 8. AutoCorrectEngine: Target word with insertion only
    // =========================================================================

    @Test
    fun `autoCorrect - target word with insertion only`() {
        trie.insert("cat", 100)

        // "ct" missing 'a' (needs insertion to become "cat")
        val result = autoCorrectEngine.getCorrections("ct", maxDistance = 1)
        assertTrue("Expected 'cat' for 'ct'", result.contains("cat"))
    }

    // =========================================================================
    // 9. AutoCorrectEngine: Target word with deletion only
    // =========================================================================

    @Test
    fun `autoCorrect - target word with deletion only`() {
        trie.insert("cat", 100)

        // "caat" has extra 'a' (needs deletion to become "cat")
        val result = autoCorrectEngine.getCorrections("caat", maxDistance = 1)
        assertTrue("Expected 'cat' for 'caat'", result.contains("cat"))
    }

    // =========================================================================
    // 10. AutoCorrectEngine: Standalone computeLevenshteinDistance
    // =========================================================================

    @Test
    fun `computeLevenshteinDistance - empty vs empty returns 0`() {
        assertEquals(0, autoCorrectEngine.computeLevenshteinDistance("", ""))
    }

    @Test
    fun `computeLevenshteinDistance - empty vs non-empty returns length`() {
        assertEquals(3, autoCorrectEngine.computeLevenshteinDistance("", "abc"))
        assertEquals(5, autoCorrectEngine.computeLevenshteinDistance("hello", ""))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("a", ""))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("", "z"))
    }

    @Test
    fun `computeLevenshteinDistance - exact strings return 0`() {
        assertEquals(0, autoCorrectEngine.computeLevenshteinDistance("keyboard", "keyboard"))
        assertEquals(0, autoCorrectEngine.computeLevenshteinDistance("a", "a"))
    }

    @Test
    fun `computeLevenshteinDistance - single character differences`() {
        // Insertion
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("cat", "cats"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("cat", "scat"))

        // Deletion
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("cats", "cat"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("scat", "cat"))

        // Substitution
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("cat", "cot"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("cat", "bat"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("cat", "car"))
    }

    @Test
    fun `computeLevenshteinDistance - transpositions`() {
        // Start transposition
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("ba", "ab"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("tsar", "star"))

        // Middle transposition
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("waht", "what"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("claen", "clean"))

        // End transposition
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("teh", "the"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("fomr", "form"))
        assertEquals(1, autoCorrectEngine.computeLevenshteinDistance("helol", "hello"))
    }

    @Test
    fun `computeLevenshteinDistance - multiple edits`() {
        // 2 substitutions
        assertEquals(2, autoCorrectEngine.computeLevenshteinDistance("pale", "bake"))

        // 2 deletions
        assertEquals(2, autoCorrectEngine.computeLevenshteinDistance("catty", "cat"))

        // 2 insertions
        assertEquals(2, autoCorrectEngine.computeLevenshteinDistance("cat", "catch"))

        // Classical Levenshtein example
        assertEquals(3, autoCorrectEngine.computeLevenshteinDistance("kitten", "sitting"))

        // Disjoint strings of equal length
        assertEquals(3, autoCorrectEngine.computeLevenshteinDistance("abc", "xyz"))
    }

    @Test
    fun `computeLevenshteinDistance - distance symmetry`() {
        val pairs = listOf(
            "cat" to "cot",
            "recive" to "receive",
            "teh" to "the",
            "sitting" to "kitten",
            "" to "abc",
            "waht" to "what"
        )
        for ((s1, s2) in pairs) {
            assertEquals(
                "Distance should be symmetric for ('$s1', '$s2')",
                autoCorrectEngine.computeLevenshteinDistance(s1, s2),
                autoCorrectEngine.computeLevenshteinDistance(s2, s1)
            )
        }
    }

    // =========================================================================
    // 11. AutoCorrectEngine: Candidate ranking
    // =========================================================================

    @Test
    fun `candidate ranking - lower distance is strictly preferred over higher distance`() {
        // "cot" has distance 1 from "cxt", frequency 10
        trie.insert("cot", 10)
        // "cart" has distance 2 from "cxt", frequency 10000
        trie.insert("cart", 10000)

        val results = autoCorrectEngine.getCorrections("cxt", maxDistance = 2)
        assertEquals("Candidate with distance 1 must rank before candidate with distance 2", "cot", results[0])
        assertEquals("cart", results[1])
    }

    @Test
    fun `candidate ranking - frequency tie-breaking when distance is identical`() {
        // All have distance 1 from "cxt"
        trie.insert("cat", 200)
        trie.insert("cut", 50)
        trie.insert("cot", 20)

        val results = autoCorrectEngine.getCorrections("cxt", maxDistance = 1)
        assertEquals(listOf("cat", "cut", "cot"), results)
    }

    @Test
    fun `candidate ranking - alphabetical tie-breaking when distance and frequency are identical`() {
        // All have distance 1 from "bxt" and same frequency 100
        trie.insert("bit", 100)
        trie.insert("bat", 100)
        trie.insert("bet", 100)

        val results = autoCorrectEngine.getCorrections("bxt", maxDistance = 1)
        assertEquals(listOf("bat", "bet", "bit"), results)
    }

    @Test
    fun `candidate ranking - runtime frequency provider boosts candidate rank`() {
        // Trie frequencies: "cat" (50), "cot" (20)
        trie.insert("cat", 50)
        trie.insert("cot", 20)

        // Runtime map gives "cot" a boosted frequency of 200
        val engineWithProvider = AutoCorrectEngine(trie) {
            mapOf("cot" to 200)
        }

        val results = engineWithProvider.getCorrections("cxt", maxDistance = 1)
        assertEquals("Boosted 'cot' should rank ahead of 'cat'", "cot", results[0])
        assertEquals("cat", results[1])
    }

    // =========================================================================
    // 12. AutoCorrectEngine: Limit constraints
    // =========================================================================

    @Test
    fun `limit constraints - limit less than or equal to zero returns empty list`() {
        trie.insert("cat", 100)
        trie.insert("cot", 80)

        assertTrue(autoCorrectEngine.getCorrections("cxt", limit = 0).isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("cxt", limit = -1).isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("cxt", limit = -10).isEmpty())

        // Even with exact match, limit <= 0 returns empty list
        assertTrue(autoCorrectEngine.getCorrections("cat", limit = 0).isEmpty())
        assertTrue(autoCorrectEngine.getCorrections("cat", limit = -1).isEmpty())
    }

    @Test
    fun `limit constraints - limit equals one returns at most one candidate`() {
        trie.insert("cat", 100)
        trie.insert("cot", 80)
        trie.insert("cut", 60)

        val results = autoCorrectEngine.getCorrections("cxt", limit = 1)
        assertEquals(1, results.size)
        assertEquals("cat", results[0])
    }

    @Test
    fun `limit constraints - limit greater than number of candidates returns all available`() {
        trie.insert("cat", 100)
        trie.insert("cot", 80)

        val results = autoCorrectEngine.getCorrections("cxt", limit = 10)
        assertEquals(2, results.size)
        assertEquals(listOf("cat", "cot"), results)
    }

    // =========================================================================
    // 13. AutoCorrectEngine: Trie is empty
    // =========================================================================

    @Test
    fun `autoCorrect - when Trie is empty returns empty list`() {
        val emptyTrie = Trie()
        val engine = AutoCorrectEngine(emptyTrie)

        assertTrue(engine.getCorrections("hello").isEmpty())
        assertTrue(engine.getCorrections("").isEmpty())
        assertTrue(engine.getCorrections("a").isEmpty())
        assertTrue(engine.getCorrections("test", maxDistance = 2, limit = 5).isEmpty())
    }

    // =========================================================================
    // 14. AutocompleteEngine: getAutocomplete corner cases
    // =========================================================================

    @Test
    fun `autocomplete - empty prefix returns empty list`() = runTest(testDispatcher) {
        trie.insert("hello", 100)
        val result = autocompleteEngine.getAutocomplete("")
        assertTrue("Empty prefix should yield empty autocomplete", result.isEmpty())
    }

    @Test
    fun `autocomplete - whitespace only prefix returns empty list`() = runTest(testDispatcher) {
        trie.insert("hello", 100)
        assertTrue(autocompleteEngine.getAutocomplete(" ").isEmpty())
        assertTrue(autocompleteEngine.getAutocomplete("   ").isEmpty())
        assertTrue(autocompleteEngine.getAutocomplete("\t\t").isEmpty())
    }

    @Test
    fun `autocomplete - valid prefix returns top suggestions ranked by frequency`() = runTest(testDispatcher) {
        trie.insert("hello", 150)
        trie.insert("help", 40)
        trie.insert("held", 10)
        trie.insert("helmet", 25)

        val result = autocompleteEngine.getAutocomplete("hel", limit = 3)
        assertEquals(listOf("hello", "help", "helmet"), result)
    }

    @Test
    fun `autocomplete - case insensitivity and whitespace trimming`() = runTest(testDispatcher) {
        trie.insert("hello", 150)
        trie.insert("help", 40)

        val resultUpper = autocompleteEngine.getAutocomplete("HEL", limit = 2)
        assertEquals(listOf("hello", "help"), resultUpper)

        val resultWhitespace = autocompleteEngine.getAutocomplete("  hel  ", limit = 2)
        assertEquals(listOf("hello", "help"), resultWhitespace)
    }

    @Test
    fun `autocomplete - non-existent short prefix (length under 3) returns empty list`() = runTest(testDispatcher) {
        trie.insert("hello", 100)

        // "zz" has no prefix matches and length < 3 does not trigger auto-correct fallback
        val result = autocompleteEngine.getAutocomplete("zz")
        assertTrue("Non-existent short prefix should return empty list", result.isEmpty())
    }

    @Test
    fun `autocomplete - non-existent prefix (length 3 or more) triggers auto-correct fallback`() = runTest(testDispatcher) {
        trie.insert("the", 1000)

        // "teh" has no prefix matches in Trie, but length >= 3 triggers AutoCorrectEngine
        val result = autocompleteEngine.getAutocomplete("teh", limit = 3)
        assertTrue("Expected 'the' via auto-correct fallback for 'teh'", result.contains("the"))
        assertEquals("the", result.first())
    }

    @Test
    fun `autocomplete - non-existent prefix with no close typos returns empty list`() = runTest(testDispatcher) {
        trie.insert("apple", 100)
        trie.insert("banana", 100)

        val result = autocompleteEngine.getAutocomplete("zzzzzz", limit = 3)
        assertTrue("Completely disjoint non-existent prefix should return empty list", result.isEmpty())
    }

    @Test
    fun `autocomplete - custom limit constraints`() = runTest(testDispatcher) {
        trie.insert("hello", 100)
        trie.insert("help", 90)
        trie.insert("held", 80)

        // limit = 1
        val single = autocompleteEngine.getAutocomplete("hel", limit = 1)
        assertEquals(listOf("hello"), single)

        // limit = 0
        val zero = autocompleteEngine.getAutocomplete("hel", limit = 0)
        assertTrue(zero.isEmpty())

        // limit < 0
        val negative = autocompleteEngine.getAutocomplete("hel", limit = -1)
        assertTrue(negative.isEmpty())

        // limit > count
        val all = autocompleteEngine.getAutocomplete("hel", limit = 10)
        assertEquals(listOf("hello", "help", "held"), all)
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    private class FakeLearnedWordDao : LearnedWordDao {
        val words = mutableMapOf<String, LearnedWordEntity>()

        override suspend fun insertOrUpdate(word: LearnedWordEntity) {
            words[word.word] = word
        }

        override suspend fun getWord(word: String): LearnedWordEntity? = words[word]

        override suspend fun getLearnedPrefixSuggestions(prefix: String, limit: Int): List<LearnedWordEntity> {
            return words.values.filter { it.word.startsWith(prefix) }
                .sortedByDescending { it.frequency }
                .take(limit)
        }

        override suspend fun getRecentlyUsedWords(limit: Int): List<LearnedWordEntity> {
            return words.values.sortedByDescending { it.lastUsedTimestamp }.take(limit)
        }

        override suspend fun getTopFrequentWords(limit: Int): List<LearnedWordEntity> {
            return words.values.sortedByDescending { it.frequency }.take(limit)
        }

        override suspend fun getAllWords(): List<LearnedWordEntity> = words.values.toList()

        override suspend fun getCount(): Int = words.size
    }
}
