package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "funny_scripts")
data class ScriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val comedyStyle: String,
    val scenesJson: String,
    val prompt: String,
    val duration: String,
    val format: String,
    val isDaily: Boolean = false,
    val dateString: String = "",
    val isFavorite: Boolean = false
)
