package com.android.keyboard.gboardclone

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Setup and testing activity for AKeyboard.
 * Provides quick shortcuts to enable the IME, select it, test predictions in real-time,
 * and view Room DB learning statistics.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable = findViewById<MaterialButton>(R.id.btn_enable_keyboard)
        val btnSelect = findViewById<MaterialButton>(R.id.btn_select_keyboard)
        val tvDictStats = findViewById<TextView>(R.id.tv_dictionary_stats)
        val tvLearnStats = findViewById<TextView>(R.id.tv_learning_stats)

        btnEnable.setOnClickListener {
            // Open system input method settings
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnSelect.setOnClickListener {
            // Show system input method picker dialog
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

        // Periodically refresh stats
        lifecycleScope.launch {
            while (true) {
                val app = KeyboardApplication.instance
                val dictSize = app.trie.size
                tvDictStats.text = "Trie Dictionary: $dictSize words loaded in memory"

                try {
                    val learnedCount = app.database.learnedWordDao().getCount()
                    tvLearnStats.text = "Room DB User Learned Words: $learnedCount persisted"
                } catch (e: Exception) {
                    tvLearnStats.text = "Room DB User Learned Words: 0"
                }
                delay(1500)
            }
        }
    }
}

