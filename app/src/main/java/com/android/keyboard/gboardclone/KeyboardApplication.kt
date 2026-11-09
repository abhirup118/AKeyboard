package com.android.keyboard.gboardclone

import android.app.Application
import com.android.keyboard.gboardclone.data.db.KeyboardDatabase
import com.android.keyboard.gboardclone.data.loader.DictionaryLoader
import com.android.keyboard.gboardclone.data.repository.SuggestionRepository
import com.android.keyboard.gboardclone.data.trie.Trie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application class for AKeyboard.
 * Initializes singleton instances for Trie, Room Database, and SuggestionRepository.
 */
class KeyboardApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var database: KeyboardDatabase
        private set

    lateinit var trie: Trie
        private set

    lateinit var repository: SuggestionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Initialize Room Database for user learning
        database = KeyboardDatabase.getInstance(this)

        // 2. Initialize in-memory Trie
        trie = Trie()

        // 3. Initialize SuggestionRepository
        repository = SuggestionRepository(
            trie = trie,
            learnedWordDao = database.learnedWordDao(),
            coroutineScope = applicationScope
        )

        // 4. Asynchronously preload the 50,000+ words dictionary asset
        applicationScope.launch {
            DictionaryLoader.loadFromAsset(
                context = this@KeyboardApplication,
                trie = trie,
                repository = repository
            )
        }
    }

    companion object {
        lateinit var instance: KeyboardApplication
            private set
    }
}

