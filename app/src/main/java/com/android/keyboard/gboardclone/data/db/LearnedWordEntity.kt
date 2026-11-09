package com.android.keyboard.gboardclone.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing user-learned words, custom additions, and usage frequencies.
 */
@Entity(tableName = "learned_words")
data class LearnedWordEntity(
    @PrimaryKey
    @ColumnInfo(name = "word")
    val word: String,

    @ColumnInfo(name = "frequency")
    val frequency: Int,

    @ColumnInfo(name = "last_used_timestamp")
    val lastUsedTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = false
)

