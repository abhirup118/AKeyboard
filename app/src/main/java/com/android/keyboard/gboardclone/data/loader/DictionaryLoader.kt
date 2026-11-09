package com.android.keyboard.gboardclone.data.loader

import android.content.Context
import android.util.Log
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.data.trie.Trie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Loads the initial 50,000+ words dictionary asset into the Trie and frequency map.
 */
object DictionaryLoader {

    private const val TAG = "DictionaryLoader"
    private const val DEFAULT_DICTIONARY_FILE = "dictionary.txt"

    /**
     * Reads [fileName] from Android assets and populates [trie] and [repository].
     * Runs off the main thread on [Dispatchers.IO].
     */
    suspend fun loadFromAsset(
        context: Context,
        trie: Trie,
        repository: SuggestionRepository,
        fileName: String = DEFAULT_DICTIONARY_FILE
    ): Int = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var count = 0

        try {
            context.assets.open(fileName).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val parts = trimmed.split(" ")
                            if (parts.isNotEmpty()) {
                                val word = parts[0].lowercase().trim()
                                val freq = if (parts.size > 1) {
                                    parts[1].toIntOrNull() ?: 1
                                } else 1

                                trie.insert(word, freq)
                                repository.seedDictionaryFrequency(word, freq)
                                count++
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
            val elapsed = System.currentTimeMillis() - startTime
            Log.i(TAG, "Successfully loaded $count words from $fileName into Trie in ${elapsed}ms")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading dictionary from $fileName", e)
        }

        count
    }
}

