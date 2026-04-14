package com.app.jerometranslator.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TranslationHistoryEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationHistoryDao(): TranslationHistoryDao
}
