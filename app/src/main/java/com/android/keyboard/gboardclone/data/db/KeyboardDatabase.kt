package com.android.keyboard.gboardclone.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database storing user-learned words and personalization data.
 */
@Database(entities = [LearnedWordEntity::class], version = 1, exportSchema = false)
abstract class KeyboardDatabase : RoomDatabase() {

    abstract fun learnedWordDao(): LearnedWordDao

    companion object {
        @Volatile
        private var INSTANCE: KeyboardDatabase? = null

        fun getInstance(context: Context): KeyboardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KeyboardDatabase::class.java,
                    "akeyboard_user_learning.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

