package com.android.keyboard.gboardclone.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.android.keyboard.gboardclone.R
import com.android.keyboard.gboardclone.viewmodel.KeyboardMode
import com.android.keyboard.gboardclone.viewmodel.ShiftState

/**
 * Custom KeyboardView displaying QWERTY letters, numbers, symbols,
 * shift states, long-press alternate characters, and repeat backspace.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        const val CODE_SHIFT = -1
        const val CODE_BACKSPACE = -2
        const val CODE_ENTER = -3
        const val CODE_MODE_SWITCH = -4
        const val CODE_EMOJI = -5
        const val CODE_MORE_SYMBOLS = -6
        const val CODE_SPACE = 32

        private const val LONG_PRESS_TIMEOUT_MS = 380L
        private const val REPEAT_INITIAL_DELAY_MS = 350L
        private const val REPEAT_INTERVAL_MS = 50L
    }

    interface KeyActionListener {
        fun onKey(primaryCode: Int, character: Char?)
        fun onAlternateKey(alternateCode: Int, character: Char?)
        fun onShiftClicked()
        fun onBackspaceClicked()
        fun onEnterClicked()
        fun onModeSwitchClicked()
        fun onMoreSymbolsClicked()
        fun onEmojiClicked()
    }

    var keyActionListener: KeyActionListener? = null

    private var isDarkTheme = false
    private var currentShiftState = ShiftState.OFF
    private var currentMode = KeyboardMode.LETTERS

    private val handler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.BOTTOM
        setPadding(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(8))
        renderKeyboard()
    }

    fun setShiftState(shiftState: ShiftState) {
        this.currentShiftState = shiftState
        renderKeyboard()
    }

    fun setKeyboardMode(mode: KeyboardMode) {
        this.currentMode = mode
        renderKeyboard()
    }

    fun setTheme(dark: Boolean) {
        this.isDarkTheme = dark
        val bgColor = ContextCompat.getColor(
            context,
            if (dark) R.color.kbd_dark_bg else R.color.kbd_light_bg
        )
        setBackgroundColor(bgColor)
        renderKeyboard()
    }

    /**
     * Renders keyboard rows according to [currentMode] and [currentShiftState].
     */
    private fun renderKeyboard() {
        removeAllViews()

        val rows = when (currentMode) {
            KeyboardMode.LETTERS -> getLetterRows()
            KeyboardMode.SYMBOLS -> getSymbolRows()
            KeyboardMode.MORE_SYMBOLS -> getMoreSymbolRows()
        }

        for (rowKeys in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f)
                weightSum = rowKeys.sumOf { it.weight.toDouble() }.toFloat()
            }

            for (key in rowKeys) {
                val keyView = createKeyView(key)
                rowLayout.addView(keyView)
            }
            addView(rowLayout)
        }
    }

    /**
     * Creates an individual key view with label, alternate hint, touch handling,
     * long-press support, and haptic feedback.
     */
    private fun createKeyView(key: KeyItem): View {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, key.weight).apply {
                setMargins(dpToPx(2), dpToPx(3), dpToPx(2), dpToPx(3))
            }
            isClickable = true
            isFocusable = true
        }

        applyKeyBackground(container, key)

        // Alternate character badge in top right (e.g. number over letters in top row)
        if (key.alternateLabel != null) {
            val altTv = TextView(context).apply {
                text = key.alternateLabel
                textSize = 9f
                gravity = Gravity.END
                setPadding(0, dpToPx(1), dpToPx(3), 0)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isDarkTheme) R.color.kbd_dark_text_secondary else R.color.kbd_light_text_secondary
                    )
                )
            }
            container.addView(altTv)
        }

        // Primary Key Label or Icon
        if (key.iconResId != null) {
            val iv = ImageView(context).apply {
                setImageResource(key.iconResId)
                layoutParams = LayoutParams(dpToPx(22), dpToPx(22)).apply {
                    gravity = Gravity.CENTER
                }
                setColorFilter(
                    if (key.code == CODE_ENTER) {
                        Color.WHITE
                    } else if (key.code == CODE_SHIFT && currentShiftState != ShiftState.OFF) {
                        ContextCompat.getColor(
                            context,
                            if (isDarkTheme) R.color.kbd_dark_accent else R.color.kbd_light_accent
                        )
                    } else {
                        ContextCompat.getColor(
                            context,
                            if (isDarkTheme) R.color.kbd_dark_text_primary else R.color.kbd_light_text_primary
                        )
                    }
                )
            }
            container.addView(iv)
        } else {
            val labelTv = TextView(context).apply {
                val displayLabel = when {
                    key.isLetter && (currentShiftState == ShiftState.SHIFTED || currentShiftState == ShiftState.CAPS_LOCK) -> {
                        key.primaryLabel.uppercase()
                    }
                    else -> key.primaryLabel
                }
                text = displayLabel
                textSize = if (key.primaryLabel.length > 2) 13f else 19f
                gravity = Gravity.CENTER
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (isDarkTheme) R.color.kbd_dark_text_primary else R.color.kbd_light_text_primary
                    )
                )
            }
            container.addView(labelTv)
        }

        setupKeyTouchListener(container, key)
        return container
    }

    private fun applyKeyBackground(view: View, key: KeyItem) {
        when {
            key.code == CODE_ENTER -> {
                view.setBackgroundResource(R.drawable.kbd_key_accent)
            }
            key.isFunctional -> {
                view.setBackgroundResource(
                    if (isDarkTheme) R.drawable.kbd_key_functional_dark else R.drawable.kbd_key_functional_light
                )
            }
            else -> {
                view.setBackgroundResource(
                    if (isDarkTheme) R.drawable.kbd_key_bg_dark else R.drawable.kbd_key_bg_light
                )
            }
        }
    }

    private fun setupKeyTouchListener(view: View, key: KeyItem) {
        var isLongPressed = false

        val longPressRunnable = Runnable {
            isLongPressed = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

            if (key.code == CODE_BACKSPACE) {
                // Continuous backspace repeat
                startBackspaceRepeat()
            } else if (key.alternateLabel != null) {
                // Input alternate character on long-press
                val altChar = key.alternateLabel.firstOrNull()
                keyActionListener?.onAlternateKey(altChar?.code ?: 0, altChar)
            }
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isLongPressed = false
                    view.isPressed = true
                    handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    stopBackspaceRepeat()
                    view.isPressed = false

                    if (!isLongPressed) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        dispatchKeyAction(key)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    stopBackspaceRepeat()
                    view.isPressed = false
                    true
                }
                else -> false
            }
        }
    }

    private fun dispatchKeyAction(key: KeyItem) {
        when (key.code) {
            CODE_SHIFT -> keyActionListener?.onShiftClicked()
            CODE_BACKSPACE -> keyActionListener?.onBackspaceClicked()
            CODE_ENTER -> keyActionListener?.onEnterClicked()
            CODE_MODE_SWITCH -> keyActionListener?.onModeSwitchClicked()
            CODE_MORE_SYMBOLS -> keyActionListener?.onMoreSymbolsClicked()
            CODE_EMOJI -> keyActionListener?.onEmojiClicked()
            CODE_SPACE -> keyActionListener?.onKey(CODE_SPACE, ' ')
            else -> {
                val char = if (key.primaryLabel.length == 1) {
                    val c = key.primaryLabel.first()
                    if (key.isLetter && (currentShiftState == ShiftState.SHIFTED || currentShiftState == ShiftState.CAPS_LOCK)) {
                        c.uppercaseChar()
                    } else {
                        c.lowercaseChar()
                    }
                } else null
                keyActionListener?.onKey(key.code, char)
            }
        }
    }

    private fun startBackspaceRepeat() {
        stopBackspaceRepeat()
        repeatRunnable = object : Runnable {
            override fun run() {
                keyActionListener?.onBackspaceClicked()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        repeatRunnable?.let { handler.postDelayed(it, REPEAT_INITIAL_DELAY_MS) }
    }

    private fun stopBackspaceRepeat() {
        repeatRunnable?.let { handler.removeCallbacks(it) }
        repeatRunnable = null
    }

    // ==========================================
    // Layout Key Definitions
    // ==========================================

    private fun getLetterRows(): List<List<KeyItem>> {
        return listOf(
            // Row 1: Q W E R T Y U I O P (with alternate numbers 1-0)
            listOf(
                KeyItem("q", "1", 'q'.code, isLetter = true),
                KeyItem("w", "2", 'w'.code, isLetter = true),
                KeyItem("e", "3", 'e'.code, isLetter = true),
                KeyItem("r", "4", 'r'.code, isLetter = true),
                KeyItem("t", "5", 't'.code, isLetter = true),
                KeyItem("y", "6", 'y'.code, isLetter = true),
                KeyItem("u", "7", 'u'.code, isLetter = true),
                KeyItem("i", "8", 'i'.code, isLetter = true),
                KeyItem("o", "9", 'o'.code, isLetter = true),
                KeyItem("p", "0", 'p'.code, isLetter = true)
            ),
            // Row 2: A S D F G H J K L (with alternate punctuation)
            listOf(
                KeyItem("a", "@", 'a'.code, isLetter = true),
                KeyItem("s", "#", 's'.code, isLetter = true),
                KeyItem("d", "$", 'd'.code, isLetter = true),
                KeyItem("f", "%", 'f'.code, isLetter = true),
                KeyItem("g", "&", 'g'.code, isLetter = true),
                KeyItem("h", "-", 'h'.code, isLetter = true),
                KeyItem("j", "+", 'j'.code, isLetter = true),
                KeyItem("k", "(", 'k'.code, isLetter = true),
                KeyItem("l", ")", 'l'.code, isLetter = true)
            ),
            // Row 3: Shift, Z X C V B N M, Backspace
            listOf(
                KeyItem("", null, CODE_SHIFT, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_shift),
                KeyItem("z", "*", 'z'.code, isLetter = true),
                KeyItem("x", "\"", 'x'.code, isLetter = true),
                KeyItem("c", "'", 'c'.code, isLetter = true),
                KeyItem("v", ":", 'v'.code, isLetter = true),
                KeyItem("b", ";", 'b'.code, isLetter = true),
                KeyItem("n", "!", 'n'.code, isLetter = true),
                KeyItem("m", "?", 'm'.code, isLetter = true),
                KeyItem("", null, CODE_BACKSPACE, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_backspace)
            ),
            // Row 4: ?123, Emoji, comma, Space, period, Enter
            listOf(
                KeyItem("?123", null, CODE_MODE_SWITCH, weight = 1.4f, isFunctional = true),
                KeyItem("", null, CODE_EMOJI, weight = 1.0f, isFunctional = true, iconResId = R.drawable.ic_keyboard_emoji),
                KeyItem(",", null, ','.code, weight = 1.0f),
                KeyItem("space", null, CODE_SPACE, weight = 4.2f),
                KeyItem(".", null, '.'.code, weight = 1.0f),
                KeyItem("", null, CODE_ENTER, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_enter)
            )
        )
    }

    private fun getSymbolRows(): List<List<KeyItem>> {
        return listOf(
            // Row 1: Numbers
            listOf(
                KeyItem("1", null, '1'.code),
                KeyItem("2", null, '2'.code),
                KeyItem("3", null, '3'.code),
                KeyItem("4", null, '4'.code),
                KeyItem("5", null, '5'.code),
                KeyItem("6", null, '6'.code),
                KeyItem("7", null, '7'.code),
                KeyItem("8", null, '8'.code),
                KeyItem("9", null, '9'.code),
                KeyItem("0", null, '0'.code)
            ),
            // Row 2: Standard symbols
            listOf(
                KeyItem("@", null, '@'.code),
                KeyItem("#", null, '#'.code),
                KeyItem("$", null, '$'.code),
                KeyItem("%", null, '%'.code),
                KeyItem("&", null, '&'.code),
                KeyItem("-", null, '-'.code),
                KeyItem("+", null, '+'.code),
                KeyItem("(", null, '('.code),
                KeyItem(")", null, ')'.code),
                KeyItem("/", null, '/'.code)
            ),
            // Row 3: More symbols toggle, symbols, backspace
            listOf(
                KeyItem("=\\<", null, CODE_MORE_SYMBOLS, weight = 1.4f, isFunctional = true),
                KeyItem("*", null, '*'.code),
                KeyItem("\"", null, '"'.code),
                KeyItem("'", null, '\''.code),
                KeyItem(":", null, ':'.code),
                KeyItem(";", null, ';'.code),
                KeyItem("!", null, '!'.code),
                KeyItem("?", null, '?'.code),
                KeyItem("", null, CODE_BACKSPACE, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_backspace)
            ),
            // Row 4: ABC, Emoji, comma, space, period, Enter
            listOf(
                KeyItem("ABC", null, CODE_MODE_SWITCH, weight = 1.4f, isFunctional = true),
                KeyItem("", null, CODE_EMOJI, weight = 1.0f, isFunctional = true, iconResId = R.drawable.ic_keyboard_emoji),
                KeyItem(",", null, ','.code, weight = 1.0f),
                KeyItem("space", null, CODE_SPACE, weight = 4.2f),
                KeyItem(".", null, '.'.code, weight = 1.0f),
                KeyItem("", null, CODE_ENTER, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_enter)
            )
        )
    }

    private fun getMoreSymbolRows(): List<List<KeyItem>> {
        return listOf(
            // Row 1: Math symbols
            listOf(
                KeyItem("~", null, '~'.code),
                KeyItem("`", null, '`'.code),
                KeyItem("|", null, '|'.code),
                KeyItem("•", null, '•'.code),
                KeyItem("√", null, '√'.code),
                KeyItem("π", null, 'π'.code),
                KeyItem("÷", null, '÷'.code),
                KeyItem("×", null, '×'.code),
                KeyItem("§", null, '§'.code),
                KeyItem("Δ", null, 'Δ'.code)
            ),
            // Row 2: Brackets & currencies
            listOf(
                KeyItem("£", null, '£'.code),
                KeyItem("€", null, '€'.code),
                KeyItem("¥", null, '¥'.code),
                KeyItem("¢", null, '¢'.code),
                KeyItem("^", null, '^'.code),
                KeyItem("°", null, '°'.code),
                KeyItem("=", null, '='.code),
                KeyItem("{", null, '{'.code),
                KeyItem("}", null, '}'.code),
                KeyItem("\\", null, '\\'.code)
            ),
            // Row 3: ?123 toggle, symbols, backspace
            listOf(
                KeyItem("?123", null, CODE_MORE_SYMBOLS, weight = 1.4f, isFunctional = true),
                KeyItem("%", null, '%'.code),
                KeyItem("©", null, '©'.code),
                KeyItem("®", null, '®'.code),
                KeyItem("™", null, '™'.code),
                KeyItem("✓", null, '✓'.code),
                KeyItem("[", null, '['.code),
                KeyItem("]", null, ']'.code),
                KeyItem("", null, CODE_BACKSPACE, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_backspace)
            ),
            // Row 4: ABC, Emoji, <, space, >, Enter
            listOf(
                KeyItem("ABC", null, CODE_MODE_SWITCH, weight = 1.4f, isFunctional = true),
                KeyItem("", null, CODE_EMOJI, weight = 1.0f, isFunctional = true, iconResId = R.drawable.ic_keyboard_emoji),
                KeyItem("<", null, '<'.code, weight = 1.0f),
                KeyItem("space", null, CODE_SPACE, weight = 4.2f),
                KeyItem(">", null, '>'.code, weight = 1.0f),
                KeyItem("", null, CODE_ENTER, weight = 1.4f, isFunctional = true, iconResId = R.drawable.ic_keyboard_enter)
            )
        )
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private data class KeyItem(
        val primaryLabel: String,
        val alternateLabel: String?,
        val code: Int,
        val weight: Float = 1.0f,
        val isFunctional: Boolean = false,
        val isLetter: Boolean = false,
        val iconResId: Int? = null
    )
}

