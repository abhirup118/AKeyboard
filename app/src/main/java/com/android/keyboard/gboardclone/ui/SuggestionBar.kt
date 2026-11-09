package com.android.keyboard.gboardclone.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.android.keyboard.gboardclone.R

/**
 * Suggestion bar view showing top 3 predictions directly above the keyboard.
 *
 * Example:
 * --------------------------------
 * | hello | help | helmet |
 * --------------------------------
 */
class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val suggestionViews = ArrayList<TextView>(3)
    private val dividerViews = ArrayList<View>(2)

    var onSuggestionClickListener: ((String) -> Unit)? = null

    private var isDarkTheme = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            44f,
            resources.displayMetrics
        ).toInt()
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, heightPx)

        buildSuggestionSlots()
        applyTheme(false)
    }

    private fun buildSuggestionSlots() {
        removeAllViews()
        suggestionViews.clear()
        dividerViews.clear()

        for (i in 0 until 3) {
            val tv = TextView(context).apply {
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f)
                gravity = Gravity.CENTER
                textSize = 15f
                maxLines = 1
                isClickable = true
                isFocusable = true
                setBackgroundResource(
                    if (isDarkTheme) R.drawable.kbd_key_bg_dark else R.drawable.kbd_key_bg_light
                )
                // Center suggestion slightly emphasized like Gboard
                if (i == 1) {
                    setTypeface(typeface, Typeface.BOLD)
                }
            }
            suggestionViews.add(tv)
            addView(tv)

            // Divider between suggestions
            if (i < 2) {
                val divider = View(context).apply {
                    val dividerWidth = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        1f,
                        resources.displayMetrics
                    ).toInt()
                    val dividerHeight = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        22f,
                        resources.displayMetrics
                    ).toInt()
                    val params = LayoutParams(dividerWidth, dividerHeight).apply {
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    layoutParams = params
                }
                dividerViews.add(divider)
                addView(divider)
            }
        }
    }

    /**
     * Updates displayed suggestions (up to 3).
     */
    fun setSuggestions(suggestions: List<String>) {
        post {
            for (i in 0 until 3) {
                val tv = suggestionViews[i]
                if (i < suggestions.size) {
                    val word = suggestions[i]
                    tv.text = word
                    tv.visibility = View.VISIBLE
                    tv.setOnClickListener {
                        onSuggestionClickListener?.invoke(word)
                    }
                } else {
                    tv.text = ""
                    tv.setOnClickListener(null)
                    tv.visibility = if (suggestions.isEmpty()) View.INVISIBLE else View.INVISIBLE
                }
            }

            // Divider visibility
            if (dividerViews.size >= 2) {
                dividerViews[0].visibility = if (suggestions.size > 1) View.VISIBLE else View.INVISIBLE
                dividerViews[1].visibility = if (suggestions.size > 2) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    /**
     * Applies Light or Dark theme styling to suggestion bar and slots.
     */
    fun applyTheme(dark: Boolean) {
        this.isDarkTheme = dark
        val bgColor = ContextCompat.getColor(
            context,
            if (dark) R.color.kbd_dark_suggestion_bg else R.color.kbd_light_suggestion_bg
        )
        val textColor = ContextCompat.getColor(
            context,
            if (dark) R.color.kbd_dark_text_primary else R.color.kbd_light_text_primary
        )
        val dividerColor = ContextCompat.getColor(
            context,
            if (dark) R.color.kbd_dark_divider else R.color.kbd_light_divider
        )

        setBackgroundColor(bgColor)

        for (tv in suggestionViews) {
            tv.setTextColor(textColor)
            tv.setBackgroundResource(
                if (dark) R.drawable.kbd_key_bg_dark else R.drawable.kbd_key_bg_light
            )
        }
        for (d in dividerViews) {
            d.setBackgroundColor(dividerColor)
        }
    }
}

