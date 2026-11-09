package com.android.keyboard.gboardclone.data.repository

import com.android.keyboard.gboardclone.data.db.LearnedWordDao
import com.android.keyboard.gboardclone.data.db.LearnedWordEntity
import com.android.keyboard.gboardclone.data.trie.Trie
import com.android.keyboard.gboardclone.domain.AutoCorrectEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Central repository managing word suggestions, frequency rankings,
 * Room database user learning, and auto-corrections.
 */
class SuggestionRepository(
    private val trie: Trie,
    private val learnedWordDao: LearnedWordDao,
    private val coroutineScope: CoroutineScope,
    private val computationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {

    // Runtime frequency map: Map<String, Int>
    // Holds both dictionary frequencies and user-learned increments for fast lookup
    private val frequencyMap = ConcurrentHashMap<String, Int>()

    // AutoCorrect engine with dynamic programming Levenshtein distance
    val autoCorrectEngine = AutoCorrectEngine(trie) { frequencyMap }

    init {
        // Hydrate in-memory cache and Trie with existing Room learned words
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val learnedWords = learnedWordDao.getAllWords()
                for (entity in learnedWords) {
                    frequencyMap[entity.word] = entity.frequency
                    trie.insert(entity.word, entity.frequency)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Seeds initial dictionary frequencies loaded from asset file.
     */
    fun seedDictionaryFrequency(word: String, frequency: Int) {
        val existing = frequencyMap[word] ?: 0
        if (frequency > existing) {
            frequencyMap[word] = frequency
        }
    }

    /**
     * Called whenever a user completes a word (e.g. presses space, punctuation, or enter).
     *
     * 1. Increments runtime frequency map count
     * 2. Increments Trie frequency
     * 3. Persists update to Room Database asynchronously without blocking UI
     */
    fun onWordCompleted(word: String) {
        val clean = word.lowercase().trim()
        if (clean.length < 2 || !clean.all { it.isLetter() }) return

        // 1. Update in-memory frequency map
        val newFreq = frequencyMap.compute(clean) { _, current -> (current ?: 0) + 1 } ?: 1

        // 2. Increment in Trie
        trie.incrementFrequency(clean, 1)

        // 3. Asynchronously persist to Room Database
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val existing = learnedWordDao.getWord(clean)
                val isCustom = existing?.isCustom ?: !trie.search(clean)
                val entity = LearnedWordEntity(
                    word = clean,
                    frequency = newFreq,
                    lastUsedTimestamp = System.currentTimeMillis(),
                    isCustom = isCustom
                )
                learnedWordDao.insertOrUpdate(entity)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Retrieves up to [limit] suggestions for the given [prefix].
     *
     * Pipeline:
     * 1. Prefix search from Trie (O(prefix_length))
     * 2. Re-rank prefix matches using active frequencyMap
     * 3. If exact matches < [limit] and prefix length >= 3, query AutoCorrectEngine
     * 4. Deduplicate and return top [limit] suggestions
     *
     * Performance: Under 20ms.
     */
    suspend fun getCombinedSuggestions(
        prefix: String,
        limit: Int = 3
    ): List<String> = withContext(computationDispatcher) {
        if (prefix.isBlank() || limit <= 0) return@withContext emptyList()
        val cleanPrefix = prefix.lowercase().trim()

        // 1. Fetch prefix suggestions from Trie
        val trieResults = trie.getSuggestions(cleanPrefix, limit = limit * 2)

        // 2. Re-score candidates with active frequencyMap to favor learned words
        val candidates = trieResults.map { pair ->
            val runtimeFreq = frequencyMap[pair.first] ?: pair.second
            pair.first to runtimeFreq
        }.sortedWith(
            compareByDescending<Pair<String, Int>> { it.second }
                .thenBy { it.first }
        ).map { it.first }.toMutableList()

        // 3. If we don't have enough suggestions or the user might have made a typo,
        // complement with DP Levenshtein auto-corrections
        if (candidates.size < limit && cleanPrefix.length >= 3) {
            val corrections = autoCorrectEngine.getCorrections(
                word = cleanPrefix,
                maxDistance = 2,
                limit = limit
            )
            for (corr in corrections) {
                if (!candidates.contains(corr)) {
                    candidates.add(corr)
                }
                if (candidates.size >= limit) break
            }
        }

        candidates.take(limit)
    }

    /**
     * Returns a snapshot of the runtime frequency map.
     */
    fun getFrequencyMap(): Map<String, Int> = HashMap(frequencyMap)
}
