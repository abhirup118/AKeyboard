package com.android.keyboard.gboardclone

import com.android.keyboard.gboardclone.data.db.LearnedWordDao
import com.android.keyboard.gboardclone.data.db.LearnedWordEntity
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.data.trie.Trie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionRepositoryTest {

    private lateinit var trie: Trie
    private lateinit var fakeDao: FakeLearnedWordDao
    private lateinit var repository: SuggestionRepository
    private val testDispatcher = StandardTestDispatcher()
    private val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setUp() {
        trie = Trie()
        fakeDao = FakeLearnedWordDao()
        repository = SuggestionRepository(trie, fakeDao, repoScope, testDispatcher)

        // Seed with dictionary words
        trie.insert("the", 1000)
        trie.insert("receive", 300)
        trie.insert("hello", 150)
        trie.insert("help", 40)
        trie.insert("helmet", 25)
        trie.insert("held", 10)
    }

    @Test
    fun `test prefix suggestions ranked by frequency`() = runTest(testDispatcher) {
        val suggestions = repository.getCombinedSuggestions("hel", limit = 3)
        assertEquals(listOf("hello", "help", "helmet"), suggestions)
    }

    @Test
    fun `test auto correction fallback for typos`() = runTest(testDispatcher) {
        // "teh" is not a word in dictionary, should auto-correct to "the"
        val corrections = repository.getCombinedSuggestions("teh", limit = 3)
        assertTrue("Expected 'the' in suggestions for 'teh', got $corrections", corrections.contains("the"))
        assertEquals("the", corrections.first())
    }

    @Test
    fun `test auto correction fallback for recive`() = runTest(testDispatcher) {
        val corrections = repository.getCombinedSuggestions("recive", limit = 3)
        assertTrue(corrections.contains("receive"))
    }

    @Test
    fun `test learning word increments frequency and boosts rank`() = runTest(testDispatcher) {
        // Initially held has low frequency 10, help has 40
        // Complete "held" multiple times
        for (i in 1..40) {
            repository.onWordCompleted("held")
        }

        val suggestions = repository.getCombinedSuggestions("hel", limit = 3)
        // "held" frequency is now 10 + 40 = 50, higher than "help" (40)
        val heldIdx = suggestions.indexOf("held")
        val helpIdx = suggestions.indexOf("help")
        assertTrue("held (idx $heldIdx) should rank higher than help (idx $helpIdx)", heldIdx < helpIdx)
    }

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
