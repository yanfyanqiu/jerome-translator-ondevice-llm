package com.app.jerometranslator.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val targetLanguageName: String,
    val inputText: String,
    val outputText: String,
    val timestamp: Long,
)
