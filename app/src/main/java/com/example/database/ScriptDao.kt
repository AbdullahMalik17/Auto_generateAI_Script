package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM funny_scripts ORDER BY timestamp DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM funny_scripts WHERE isDaily = 1 AND dateString = :dateString ORDER BY id ASC")
    suspend fun getDailyScripts(dateString: String): List<ScriptEntity>

    @Query("SELECT * FROM funny_scripts WHERE id = :id LIMIT 1")
    suspend fun getScriptById(id: Int): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity): Long

    @Update
    suspend fun updateScript(script: ScriptEntity)

    @Query("UPDATE funny_scripts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Int, isFavorite: Boolean)

    @Query("DELETE FROM funny_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Int)
}
