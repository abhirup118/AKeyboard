package com.android.keyboard.gboardclone.domain

import com.android.keyboard.gboardclone.data.repository.SuggestionRepository

/**
 * Domain engine responsible for text prediction and autocomplete.
 */
class AutocompleteEngine(
    private val repository: SuggestionRepository
) {
    /**
     * Generates top suggestions for the current prefix.
     */
    suspend fun getAutocomplete(prefix: String, limit: Int = 3): List<String> {
        return repository.getCombinedSuggestions(prefix, limit)
    }
}

