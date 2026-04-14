package com.app.jerometranslator.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationHistoryDao {
    @Query("SELECT * FROM translation_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranslationHistoryEntity>>

    @Insert
    suspend fun insert(entry: TranslationHistoryEntity)

    @Delete
    suspend fun delete(entry: TranslationHistoryEntity)

    @Query("DELETE FROM translation_history")
    suspend fun deleteAll()
}
