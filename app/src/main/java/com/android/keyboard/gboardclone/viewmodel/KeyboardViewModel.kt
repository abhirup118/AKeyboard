package com.android.keyboard.gboardclone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.domain.AutocompleteEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shift state enumeration:
 * - OFF: Lowercase letters
 * - SHIFTED: Next single character is uppercase, then reverts to OFF
 * - CAPS_LOCK: All letters uppercase until shifted again
 */
enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK
}

/**
 * Keyboard display modes:
 * - LETTERS: Standard QWERTY layout
 * - SYMBOLS: Numbers & primary punctuation / symbols (?123)
 * - MORE_SYMBOLS: Mathematical & special characters (=\<)
 */
enum class KeyboardMode {
    LETTERS,
    SYMBOLS,
    MORE_SYMBOLS
}

/**
 * MVVM ViewModel for keyboard state management and text prediction orchestration.
 */
class KeyboardViewModel(
    private val repository: SuggestionRepository,
    private val autocompleteEngine: AutocompleteEngine = AutocompleteEngine(repository)
) : ViewModel() {

    // Current word composing buffer
    private val _currentWord = MutableStateFlow("")
    val currentWord: StateFlow<String> = _currentWord.asStateFlow()

    // Top 3 suggestions for the suggestion bar
    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // Shift / Caps Lock state
    private val _shiftState = MutableStateFlow(ShiftState.OFF)
    val shiftState: StateFlow<ShiftState> = _shiftState.asStateFlow()

    // Active keyboard layout mode
    private val _keyboardMode = MutableStateFlow(KeyboardMode.LETTERS)
    val keyboardMode: StateFlow<KeyboardMode> = _keyboardMode.asStateFlow()

    // Theme (false = light, true = dark)
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private var lastShiftClickTime = 0L
    private var predictionJob: Job? = null

    companion object {
        private const val DOUBLE_TAP_TIMEOUT_MS = 350L
        private const val SUGGESTION_LIMIT = 3
    }

    /**
     * Handles letter / character key press.
     * Returns the exact character to send to the InputConnection.
     */
    fun onCharacterPressed(char: Char): Char {
        val currentShift = _shiftState.value
        val outputChar = when (currentShift) {
            ShiftState.CAPS_LOCK, ShiftState.SHIFTED -> char.uppercaseChar()
            ShiftState.OFF -> char.lowercaseChar()
        }

        // If shifted once, revert to OFF after single letter
        if (currentShift == ShiftState.SHIFTED) {
            _shiftState.value = ShiftState.OFF
        }

        // Update composing buffer if letter
        if (outputChar.isLetter()) {
            _currentWord.value += outputChar
            refreshSuggestions(_currentWord.value)
        } else {
            // Non-letter punctuation finishes active word
            finishCurrentWord()
        }

        return outputChar
    }

    /**
     * Handles backspace tap.
     */
    fun onBackspacePressed() {
        val word = _currentWord.value
        if (word.isNotEmpty()) {
            val updated = word.dropLast(1)
            _currentWord.value = updated
            refreshSuggestions(updated)
        } else {
            _suggestions.value = emptyList()
        }
    }

    /**
     * Handles shift key tap with double-tap caps lock support.
     */
    fun onShiftPressed() {
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastShiftClickTime
        lastShiftClickTime = now

        val current = _shiftState.value
        _shiftState.value = when {
            // Double tap within threshold turns on Caps Lock
            timeSinceLast <= DOUBLE_TAP_TIMEOUT_MS && (current == ShiftState.SHIFTED || current == ShiftState.OFF) -> {
                ShiftState.CAPS_LOCK
            }
            current == ShiftState.CAPS_LOCK -> ShiftState.OFF
            current == ShiftState.SHIFTED -> ShiftState.OFF
            else -> ShiftState.SHIFTED
        }
    }

    /**
     * Toggles between letters, symbols, and alternate symbols.
     */
    fun toggleSymbolMode() {
        _keyboardMode.value = when (_keyboardMode.value) {
            KeyboardMode.LETTERS -> KeyboardMode.SYMBOLS
            KeyboardMode.SYMBOLS, KeyboardMode.MORE_SYMBOLS -> KeyboardMode.LETTERS
        }
    }

    /**
     * Toggles between secondary symbols (?123 vs =\<).
     */
    fun toggleMoreSymbols() {
        _keyboardMode.value = when (_keyboardMode.value) {
            KeyboardMode.SYMBOLS -> KeyboardMode.MORE_SYMBOLS
            KeyboardMode.MORE_SYMBOLS -> KeyboardMode.SYMBOLS
            KeyboardMode.LETTERS -> KeyboardMode.SYMBOLS
        }
    }

    /**
     * Called when a suggestion from the bar is selected.
     * Clears composing buffer, records learning, and clears suggestions.
     */
    fun onSuggestionSelected(suggestion: String) {
        repository.onWordCompleted(suggestion)
        _currentWord.value = ""
        _suggestions.value = emptyList()
    }

    /**
     * Finishes current composing word (e.g. space or enter was pressed).
     */
    fun finishCurrentWord() {
        val word = _currentWord.value
        if (word.isNotBlank()) {
            repository.onWordCompleted(word)
        }
        _currentWord.value = ""
        _suggestions.value = emptyList()
    }

    /**
     * Updates current word buffer from external context (e.g. cursor moved).
     */
    fun setCurrentWord(word: String) {
        _currentWord.value = word
        refreshSuggestions(word)
    }

    /**
     * Toggles light/dark theme.
     */
    fun setTheme(isDark: Boolean) {
        _isDarkTheme.value = isDark
    }

    /**
     * Enables auto-capitalization (Shift ON) for the start of a field or sentence,
     * unless Caps Lock is already active.
     */
    fun setAutoShift(enabled: Boolean = true) {
        if (_shiftState.value != ShiftState.CAPS_LOCK) {
            _shiftState.value = if (enabled) ShiftState.SHIFTED else ShiftState.OFF
        }
    }

    /**
     * Triggers asynchronous suggestion query in under 20ms.
     */
    private fun refreshSuggestions(prefix: String) {
        predictionJob?.cancel()
        if (prefix.isBlank()) {
            _suggestions.value = emptyList()
            return
        }

        predictionJob = viewModelScope.launch {
            val results = autocompleteEngine.getAutocomplete(prefix, SUGGESTION_LIMIT)
            _suggestions.value = results
        }
    }
}

