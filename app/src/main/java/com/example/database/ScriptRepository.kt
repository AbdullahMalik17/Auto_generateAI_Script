package com.example.database

import kotlinx.coroutines.flow.Flow

class ScriptRepository(private val scriptDao: ScriptDao) {
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()

    suspend fun getDailyScripts(dateString: String): List<ScriptEntity> {
        return scriptDao.getDailyScripts(dateString)
    }

    suspend fun getScriptById(id: Int): ScriptEntity? {
        return scriptDao.getScriptById(id)
    }

    suspend fun insertScript(script: ScriptEntity): Long {
        return scriptDao.insertScript(script)
    }

    suspend fun updateScript(script: ScriptEntity) {
        scriptDao.updateScript(script)
    }

    suspend fun updateFavorite(id: Int, isFavorite: Boolean) {
        scriptDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun deleteScript(id: Int) {
        scriptDao.deleteScriptById(id)
    }
}
