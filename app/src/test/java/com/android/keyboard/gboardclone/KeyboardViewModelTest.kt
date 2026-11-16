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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trie: Trie
    private lateinit var repository: SuggestionRepository
    private lateinit var viewModel: KeyboardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trie = Trie()
        trie.insert("hello", 100)
        trie.insert("help", 50)
        val repoScope = CoroutineScope(SupervisorJob() + testDispatcher)
        repository = SuggestionRepository(trie, MockDao(), repoScope, testDispatcher)
        viewModel = KeyboardViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test character typing and shift single letter reversion`() = runTest(testDispatcher) {
        // Initially lowercase
        val c1 = viewModel.onCharacterPressed('h')
        assertEquals('h', c1)
        assertEquals("h", viewModel.currentWord.value)
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)

        // Press Shift
        viewModel.onShiftPressed()
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        // Next letter is uppercase
        val c2 = viewModel.onCharacterPressed('e')
        assertEquals('E', c2)
        assertEquals("hE", viewModel.currentWord.value)

        // Shift automatically reverts to OFF
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
    }

    @Test
    fun `test caps lock double tap stays on`() = runTest(testDispatcher) {
        viewModel.onShiftPressed()
        // Tap shift again immediately (within threshold)
        viewModel.onShiftPressed()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        // Multiple uppercase characters stay caps lock
        val c1 = viewModel.onCharacterPressed('a')
        val c2 = viewModel.onCharacterPressed('b')
        assertEquals('A', c1)
        assertEquals('B', c2)
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        // Tapping shift again turns it off
        viewModel.onShiftPressed()
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
    }

    @Test
    fun `test backspace removes last character`() {
        viewModel.onCharacterPressed('a')
        viewModel.onCharacterPressed('b')
        viewModel.onCharacterPressed('c')
        assertEquals("abc", viewModel.currentWord.value)

        viewModel.onBackspacePressed()
        assertEquals("ab", viewModel.currentWord.value)

        viewModel.onBackspacePressed()
        assertEquals("a", viewModel.currentWord.value)
    }

    @Test
    fun `test symbol mode switching`() {
        assertEquals(KeyboardMode.LETTERS, viewModel.keyboardMode.value)

        viewModel.toggleSymbolMode()
        assertEquals(KeyboardMode.SYMBOLS, viewModel.keyboardMode.value)

        viewModel.toggleMoreSymbols()
        assertEquals(KeyboardMode.MORE_SYMBOLS, viewModel.keyboardMode.value)

        viewModel.toggleSymbolMode()
        assertEquals(KeyboardMode.LETTERS, viewModel.keyboardMode.value)
    }

    @Test
    fun `test suggestion selection clears current word`() {
        viewModel.onCharacterPressed('h')
        viewModel.onCharacterPressed('e')
        viewModel.onCharacterPressed('l')
        assertEquals("hel", viewModel.currentWord.value)

        viewModel.onSuggestionSelected("hello")
        assertEquals("", viewModel.currentWord.value)
        assertEquals(emptyList<String>(), viewModel.suggestions.value)
    }

    @Test
    fun `test setAutoShift capitalizes first letter and reverts to lowercase`() {
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)

        // Enable auto-shift (e.g. keyboard opens or sentence starts)
        viewModel.setAutoShift(true)
        assertEquals(ShiftState.SHIFTED, viewModel.shiftState.value)

        // First character typed is uppercase
        val firstChar = viewModel.onCharacterPressed('w')
        assertEquals('W', firstChar)
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)

        // Subsequent characters typed are lowercase
        val secondChar = viewModel.onCharacterPressed('o')
        assertEquals('o', secondChar)
        assertEquals(ShiftState.OFF, viewModel.shiftState.value)
    }

    @Test
    fun `test setAutoShift does not override caps lock`() {
        viewModel.onShiftPressed()
        viewModel.onShiftPressed()
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        viewModel.setAutoShift(true)
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)

        viewModel.setAutoShift(false)
        assertEquals(ShiftState.CAPS_LOCK, viewModel.shiftState.value)
    }

    private class MockDao : LearnedWordDao {
        override suspend fun insertOrUpdate(word: LearnedWordEntity) {}
        override suspend fun getWord(word: String): LearnedWordEntity? = null
        override suspend fun getLearnedPrefixSuggestions(prefix: String, limit: Int): List<LearnedWordEntity> = emptyList()
        override suspend fun getRecentlyUsedWords(limit: Int): List<LearnedWordEntity> = emptyList()
        override suspend fun getTopFrequentWords(limit: Int): List<LearnedWordEntity> = emptyList()
        override suspend fun getAllWords(): List<LearnedWordEntity> = emptyList()
        override suspend fun getCount(): Int = 0
    }
}
