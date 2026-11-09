package com.android.keyboard.gboardclone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data Access Object for persisting and querying learned words.
 */
@Dao
interface LearnedWordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(word: LearnedWordEntity)

    @Query("SELECT * FROM learned_words WHERE word = :word LIMIT 1")
    suspend fun getWord(word: String): LearnedWordEntity?

    @Query("SELECT * FROM learned_words WHERE word LIKE :prefix || '%' ORDER BY frequency DESC, last_used_timestamp DESC LIMIT :limit")
    suspend fun getLearnedPrefixSuggestions(prefix: String, limit: Int): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words ORDER BY last_used_timestamp DESC LIMIT :limit")
    suspend fun getRecentlyUsedWords(limit: Int): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words ORDER BY frequency DESC LIMIT :limit")
    suspend fun getTopFrequentWords(limit: Int): List<LearnedWordEntity>

    @Query("SELECT * FROM learned_words")
    suspend fun getAllWords(): List<LearnedWordEntity>

    @Query("SELECT COUNT(*) FROM learned_words")
    suspend fun getCount(): Int
}

