package com.android.keyboard.gboardclone.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import com.android.keyboard.gboardclone.KeyboardApplication
import com.android.keyboard.gboardclone.R
import com.android.keyboard.gboardclone.ui.KeyboardView
import com.android.keyboard.gboardclone.ui.SuggestionBar
import com.android.keyboard.gboardclone.viewmodel.KeyboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android InputMethodService (IME) powering AKeyboard.
 *
 * Coordinates:
 * - Touch input from [KeyboardView]
 * - Suggestion bar clicks from [SuggestionBar]
 * - MVVM state from [KeyboardViewModel]
 * - Cursor updates & text commits via [InputConnection]
 */
class KeyboardService : InputMethodService(), KeyboardView.KeyActionListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var viewModel: KeyboardViewModel
    private var rootView: View? = null
    private var suggestionBar: SuggestionBar? = null
    private var keyboardView: KeyboardView? = null
    private var suggestionDivider: View? = null

    override fun onCreate() {
        super.onCreate()
        val app = KeyboardApplication.instance
        viewModel = KeyboardViewModel(app.repository)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_root_view, null)
        rootView = view
        suggestionBar = view.findViewById(R.id.suggestion_bar)
        keyboardView = view.findViewById(R.id.keyboard_view)
        suggestionDivider = view.findViewById(R.id.suggestion_divider)

        keyboardView?.keyActionListener = this

        // When a suggestion slot is tapped
        suggestionBar?.onSuggestionClickListener = { selectedSuggestion ->
            onSuggestionTapped(selectedSuggestion)
        }

        // Collect ViewModel states
        observeViewModel()

        return view
    }

    private fun observeViewModel() {
        // Observe suggestions
        serviceScope.launch {
            viewModel.suggestions.collect { suggestions ->
                suggestionBar?.setSuggestions(suggestions)
            }
        }

        // Observe shift state
        serviceScope.launch {
            viewModel.shiftState.collect { shiftState ->
                keyboardView?.setShiftState(shiftState)
            }
        }

        // Observe keyboard mode (letters vs symbols)
        serviceScope.launch {
            viewModel.keyboardMode.collect { mode ->
                keyboardView?.setKeyboardMode(mode)
            }
        }

        // Observe theme
        serviceScope.launch {
            viewModel.isDarkTheme.collect { isDark ->
                applyTheme(isDark)
            }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Detect dark mode from system night mode or input field type
        val isDarkMode = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        viewModel.setTheme(isDarkMode)

        // Clear composing word on new field focus
        viewModel.finishCurrentWord()

        // Respect input field type and auto-capitalize only for sentence-capitalized text fields
        val inputType = info?.inputType ?: 0
        val isTextField = (inputType and EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT
        val isCapSentences = (inputType and EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
        viewModel.setAutoShift(isTextField && isCapSentences)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)

        // When cursor moves or selection collapses, synchronize composing word buffer
        if (newSelStart == newSelEnd) {
            val textBefore = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString() ?: ""
            val lastWord = extractLastWord(textBefore)
            if (lastWord != viewModel.currentWord.value) {
                viewModel.setCurrentWord(lastWord)
            }
        }
    }

    private fun extractLastWord(text: String): String {
        if (text.isEmpty()) return ""
        var idx = text.length - 1
        while (idx >= 0 && text[idx].isLetter()) {
            idx--
        }
        return text.substring(idx + 1)
    }

    // ==========================================
    // Key Action Callbacks from KeyboardView
    // ==========================================

    override fun onKey(primaryCode: Int, character: Char?) {
        val ic = currentInputConnection ?: return
        if (character != null) {
            val charToCommit = viewModel.onCharacterPressed(character)
            ic.commitText(charToCommit.toString(), 1)
        } else if (primaryCode == KeyboardView.CODE_SPACE) {
            // Space pressed: commit space and notify ViewModel to learn previous word
            ic.commitText(" ", 1)
            viewModel.finishCurrentWord()
        }
    }

    override fun onAlternateKey(alternateCode: Int, character: Char?) {
        val ic = currentInputConnection ?: return
        if (character != null) {
            ic.commitText(character.toString(), 1)
            viewModel.finishCurrentWord()
        }
    }

    override fun onShiftClicked() {
        viewModel.onShiftPressed()
    }

    override fun onBackspaceClicked() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
        viewModel.onBackspacePressed()
    }

    override fun onEnterClicked() {
        val ic = currentInputConnection ?: return
        val editorInfo = currentInputEditorInfo

        viewModel.finishCurrentWord()

        // Handle specific IME action (Send, Search, Done, Next, Go)
        if (editorInfo != null && (editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
            val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
            val handled = ic.performEditorAction(action)
            if (!handled) {
                sendNewline(ic)
            }
        } else {
            sendNewline(ic)
        }
    }

    private fun sendNewline(ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onModeSwitchClicked() {
        viewModel.toggleSymbolMode()
    }

    override fun onMoreSymbolsClicked() {
        viewModel.toggleMoreSymbols()
    }

    override fun onEmojiClicked() {
        val ic = currentInputConnection ?: return
        // Emoji placeholder key: commits standard smile emoji 😊
        ic.commitText("😊", 1)
    }

    /**
     * Handles selection of a suggestion from the SuggestionBar.
     * Replaces the currently typed prefix word and automatically appends a space.
     */
    private fun onSuggestionTapped(suggestion: String) {
        val ic = currentInputConnection ?: return
        val currentPrefix = viewModel.currentWord.value

        if (currentPrefix.isNotEmpty()) {
            ic.deleteSurroundingText(currentPrefix.length, 0)
        }

        // Commit full suggestion + space
        ic.commitText("$suggestion ", 1)
        viewModel.onSuggestionSelected(suggestion)
    }

    private fun applyTheme(isDark: Boolean) {
        val bgColor = ContextCompat.getColor(
            this,
            if (isDark) R.color.kbd_dark_bg else R.color.kbd_light_bg
        )
        val dividerColor = ContextCompat.getColor(
            this,
            if (isDark) R.color.kbd_dark_divider else R.color.kbd_light_divider
        )

        rootView?.setBackgroundColor(bgColor)
        suggestionDivider?.setBackgroundColor(dividerColor)
        suggestionBar?.applyTheme(isDark)
        keyboardView?.setTheme(isDark)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

