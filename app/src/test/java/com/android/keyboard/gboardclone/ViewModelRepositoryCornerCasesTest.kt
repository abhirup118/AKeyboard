package com.android.keyboard.gboardclone

import com.android.keyboard.gboardclone.data.db.LearnedWordDao
import com.android.keyboard.gboardclone.data.db.LearnedWordEntity
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.data.trie.Trie
import com.android.keyboard.gboardclone.viewmodel.KeyboardMode
import com.android.keyboard.gboardclone.viewmodel.KeyboardViewModel
import com.android.keyboard.gboardclone.viewmodel.ShiftState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelRepositoryCornerCasesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trie: Trie
    private lateinit var fakeDao: TestLearnedWordDao
    private lateinit var repository: SuggestionRepository
    private lateinit var viewModel: KeyboardViewModel
    private val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trie = Trie()
        fakeDao = TestLearnedWordDao()
        repository = SuggestionRepository(trie, fakeDao, repoScope, testDispatcher)
        viewModel = KeyboardViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =========================================================================
    // KeyboardViewModel Corner Cases
    // =========================================================================

    @Test
    fun `KeyboardViewModel - rapid shift double-tap within threshold transitions to CAPS_LOCK`() {
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)

        // First tap -> SHIFTED
        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        // Second tap immediately (< 350ms) -> CAPS_LOCK
        viewModel.onShiftPressed()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)
    }

    @Test
    fun `KeyboardViewModel - shift tapped once, wait over 350ms, tap again reverts to OFF`() {
        // First tap -> SHIFTED
        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        // Wait beyond double-tap threshold (350ms)
        Thread.sleep(400)

        // Second tap after threshold -> OFF
        viewModel.onShiftPressed()
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
    }

    @Test
    fun `KeyboardViewModel - triple-tap shift transitions to CAPS_LOCK then toggles back to OFF`() {
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)

        // Tap 1 -> SHIFTED
        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        // Tap 2 (rapid) -> CAPS_LOCK
        viewModel.onShiftPressed()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        // Tap 3 -> reverts to OFF
        viewModel.onShiftPressed()
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
    }

    @Test
    fun `KeyboardViewModel - in CAPS_LOCK typing multiple characters preserves uppercase and does NOT revert to OFF`() {
        // Activate CAPS_LOCK
        viewModel.onShiftPressed()
        viewModel.onShiftPressed()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        val c1 = viewModel.onCharacterPressed('a')
        assertEquals('A', c1)
        assertEquals("A", viewModel.currentWord.value)
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        val c2 = viewModel.onCharacterPressed('b')
        assertEquals('B', c2)
        assertEquals("AB", viewModel.currentWord.value)
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        val c3 = viewModel.onCharacterPressed('c')
        assertEquals('C', c3)
        assertEquals("ABC", viewModel.currentWord.value)
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)
    }

    @Test
    fun `KeyboardViewModel - in SHIFTED typing number reverts to OFF and finishes word`() {
        // Form word, shift, type number
        viewModel.onCharacterPressed('h')
        viewModel.onCharacterPressed('i')
        assertEquals("hi", viewModel.currentWord.value)

        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        val numChar = viewModel.onCharacterPressed('1')
        assertEquals('1', numChar)
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
        assertEquals("", viewModel.currentWord.value) // Word buffer finished
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
    }

    @Test
    fun `KeyboardViewModel - in SHIFTED typing punctuation reverts to OFF and finishes word`() {
        viewModel.onCharacterPressed('h')
        viewModel.onCharacterPressed('i')
        assertEquals("hi", viewModel.currentWord.value)

        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        val punctChar = viewModel.onCharacterPressed('.')
        assertEquals('.', punctChar)
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
    }

    @Test
    fun `KeyboardViewModel - in SHIFTED typing space reverts to OFF and finishes word`() {
        viewModel.onCharacterPressed('h')
        viewModel.onCharacterPressed('i')
        assertEquals("hi", viewModel.currentWord.value)

        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        val spaceChar = viewModel.onCharacterPressed(' ')
        assertEquals(' ', spaceChar)
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
    }

    @Test
    fun `KeyboardViewModel - backspace on empty composing buffer should not crash and sets suggestions to empty`() {
        assertEquals("", viewModel.currentWord.value)
        viewModel.onBackspacePressed()
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
    }

    @Test
    fun `KeyboardViewModel - backspace on single-letter word drops to empty buffer`() = runTest(testDispatcher) {
        viewModel.onCharacterPressed('q')
        assertEquals("q", viewModel.currentWord.value)

        viewModel.onBackspacePressed()
        assertEquals("", viewModel.currentWord.value)

        advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
    }

    @Test
    fun `KeyboardViewModel - finishCurrentWord on empty buffer vs populated buffer`() {
        // Empty buffer: should not record or crash
        viewModel.finishCurrentWord()
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
        assertTrue(repository.getFrequencyMap().isEmpty())

        // Populated buffer: records word and clears buffer
        viewModel.onCharacterPressed('r')
        viewModel.onCharacterPressed('u')
        viewModel.onCharacterPressed('n')
        assertEquals("run", viewModel.currentWord.value)

        viewModel.finishCurrentWord()
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
        assertEquals(1, repository.getFrequencyMap()["run"])
    }

    @Test
    fun `KeyboardViewModel - setCurrentWord with external text, whitespace, punctuation`() = runTest(testDispatcher) {
        // Seed Trie with word to verify suggestion generation
        trie.insert("banana", 100)

        // 1. Valid word
        viewModel.setCurrentWord("ban")
        assertEquals("ban", viewModel.currentWord.value)
        advanceUntilIdle()
        assertTrue(viewModel.suggestions.value.contains("banana"))

        // 2. Empty string
        viewModel.setCurrentWord("")
        assertEquals("", viewModel.currentWord.value)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.suggestions.value)

        // 3. Whitespace prefix
        viewModel.setCurrentWord("   ")
        assertEquals("   ", viewModel.currentWord.value)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), viewModel.suggestions.value)

        // 4. Punctuation
        viewModel.setCurrentWord("hello!")
        assertEquals("hello!", viewModel.currentWord.value)
    }

    @Test
    fun `KeyboardViewModel - toggleSymbolMode cycle (LETTERS to SYMBOLS to LETTERS)`() {
        assertEquals(KeyboardMode.LETTERS, viewModel.keyboardMode.value)

        viewModel.toggleSymbolMode()
        assertEquals(KeyboardMode.SYMBOLS, viewModel.keyboardMode.value)

        viewModel.toggleSymbolMode()
        assertEquals(KeyboardMode.LETTERS, viewModel.keyboardMode.value)

        // Also from MORE_SYMBOLS mode, toggleSymbolMode should return to LETTERS
        viewModel.toggleMoreSymbols() // LETTERS -> SYMBOLS
        viewModel.toggleMoreSymbols() // SYMBOLS -> MORE_SYMBOLS
        assertEquals(KeyboardMode.MORE_SYMBOLS, viewModel.keyboardMode.value)

        viewModel.toggleSymbolMode()
        assertEquals(KeyboardMode.LETTERS, viewModel.keyboardMode.value)
    }

    @Test
    fun `KeyboardViewModel - toggleMoreSymbols cycle (SYMBOLS to MORE_SYMBOLS to SYMBOLS)`() {
        // First switch to SYMBOLS
        viewModel.toggleSymbolMode()
        assertEquals(KeyboardMode.SYMBOLS, viewModel.keyboardMode.value)

        // Toggle to MORE_SYMBOLS
        viewModel.toggleMoreSymbols()
        assertEquals(KeyboardMode.MORE_SYMBOLS, viewModel.keyboardMode.value)

        // Toggle back to SYMBOLS
        viewModel.toggleMoreSymbols()
        assertEquals(KeyboardMode.SYMBOLS, viewModel.keyboardMode.value)
    }

    @Test
    fun `KeyboardViewModel - theme toggling true and false`() {
        assertFalse(viewModel.isDarkTheme.value)

        viewModel.setTheme(true)
        assertTrue(viewModel.isDarkTheme.value)

        viewModel.setTheme(false)
        assertFalse(viewModel.isDarkTheme.value)
    }

    @Test
    fun `KeyboardViewModel - onSuggestionSelected resets composing buffer and suggestions`() {
        viewModel.onCharacterPressed('c')
        viewModel.onCharacterPressed('a')
        viewModel.onCharacterPressed('r')
        assertEquals("car", viewModel.currentWord.value)

        viewModel.onSuggestionSelected("carrier")
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
        assertEquals(1, repository.getFrequencyMap()["carrier"])
    }

    // =========================================================================
    // SuggestionRepository Corner Cases
    // =========================================================================

    @Test
    fun `SuggestionRepository - getCombinedSuggestions with empty prefix, whitespace prefix, limit non-positive`() = runTest(testDispatcher) {
        trie.insert("test", 100)

        // Empty prefix
        val emptyResult = repository.getCombinedSuggestions("", limit = 3)
        assertTrue(emptyResult.isEmpty())

        // Whitespace prefix
        val spaceResult = repository.getCombinedSuggestions("   ", limit = 3)
        assertTrue(spaceResult.isEmpty())

        val tabNewlineResult = repository.getCombinedSuggestions("\t\n", limit = 3)
        assertTrue(tabNewlineResult.isEmpty())

        // Limit <= 0
        val zeroLimitResult = repository.getCombinedSuggestions("te", limit = 0)
        assertTrue(zeroLimitResult.isEmpty())

        val negativeLimitResult = repository.getCombinedSuggestions("te", limit = -1)
        assertTrue(negativeLimitResult.isEmpty())

        val largeNegativeLimitResult = repository.getCombinedSuggestions("te", limit = -100)
        assertTrue(largeNegativeLimitResult.isEmpty())
    }

    @Test
    fun `SuggestionRepository - onWordCompleted with 1-letter word does NOT learn`() {
        repository.onWordCompleted("a")
        repository.onWordCompleted("I")
        repository.onWordCompleted(" ")

        assertNull(repository.getFrequencyMap()["a"])
        assertNull(repository.getFrequencyMap()["i"])
        assertEquals(0, trie.getFrequency("a"))
        assertEquals(0, trie.getFrequency("i"))
        assertNull(fakeDao.words["a"])
        assertNull(fakeDao.words["i"])
    }

    @Test
    fun `SuggestionRepository - onWordCompleted with non-alphabetic string does NOT learn`() {
        val invalidInputs = listOf("123", "!@#", "$%^&*()", "word1", "1word", "a_b", "hello world", "")
        for (input in invalidInputs) {
            repository.onWordCompleted(input)
        }

        for (input in invalidInputs) {
            val clean = input.lowercase().trim()
            assertNull(repository.getFrequencyMap()[clean])
            assertEquals(0, trie.getFrequency(clean))
            assertNull(fakeDao.words[clean])
        }
    }

    @Test
    fun `SuggestionRepository - onWordCompleted with valid 2+ letter word learns, increments Trie frequency, updates frequencyMap, persists to DAO`() {
        val word = "gboard"

        // Initial completion
        repository.onWordCompleted(word)

        assertEquals(1, repository.getFrequencyMap()[word])
        assertEquals(1, trie.getFrequency(word))

        // Wait for async DAO persistence
        val persisted = fakeDao.waitForWord(word)
        assertNotNull("Word should be persisted in DAO", persisted)
        assertEquals(word, persisted?.word)
        assertEquals(1, persisted?.frequency)

        // Second completion: increments frequency
        repository.onWordCompleted(word)

        assertEquals(2, repository.getFrequencyMap()[word])
        assertEquals(2, trie.getFrequency(word))

        val updated = fakeDao.waitForWord(word, targetFrequency = 2)
        assertNotNull(updated)
        assertEquals(2, updated?.frequency)
    }

    @Test
    fun `SuggestionRepository - seedDictionaryFrequency ignores lower frequency when existing frequency is higher`() {
        val word = "apple"

        // Seed initial frequency
        repository.seedDictionaryFrequency(word, 100)
        assertEquals(100, repository.getFrequencyMap()[word])

        // Seeding lower frequency (50) should be ignored
        repository.seedDictionaryFrequency(word, 50)
        assertEquals(100, repository.getFrequencyMap()[word])

        // Seeding equal frequency (100) should not change
        repository.seedDictionaryFrequency(word, 100)
        assertEquals(100, repository.getFrequencyMap()[word])

        // Seeding higher frequency (150) should update
        repository.seedDictionaryFrequency(word, 150)
        assertEquals(150, repository.getFrequencyMap()[word])
    }

    @Test
    fun `SuggestionRepository - getFrequencyMap returns independent copy without exposing internal mutation`() {
        repository.seedDictionaryFrequency("kotlin", 50)

        val snapshot = repository.getFrequencyMap()
        assertEquals(50, snapshot["kotlin"])

        // Mutating returned map should not affect repository
        val mutableSnapshot = snapshot as MutableMap<String, Int>
        mutableSnapshot["kotlin"] = 999
        mutableSnapshot["tampered"] = 12345

        val freshMap = repository.getFrequencyMap()
        assertEquals(50, freshMap["kotlin"])
        assertNull(freshMap["tampered"])

        // Subsequent word learning should not affect older snapshot
        repository.onWordCompleted("android")
        assertNull(snapshot["android"])
        assertEquals(1, repository.getFrequencyMap()["android"])
    }

    // =========================================================================
    // Test DAO implementation
    // =========================================================================

    private class TestLearnedWordDao : LearnedWordDao {
        val words = ConcurrentHashMap<String, LearnedWordEntity>()

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

        fun waitForWord(word: String, targetFrequency: Int? = null, timeoutMs: Long = 2000): LearnedWordEntity? {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val entity = words[word]
                if (entity != null) {
                    if (targetFrequency == null || entity.frequency >= targetFrequency) {
                        return entity
                    }
                }
                Thread.sleep(10)
            }
            return words[word]
        }
    }
}
