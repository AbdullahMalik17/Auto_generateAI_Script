package com.example.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScriptScene(
    val sceneNumber: Int,
    val visualPrompt: String,
    val dialogue: String,
    val cameraAction: String,
    val audioNotes: String,
    val stylePreset: String = "Cinematic"
)

@JsonClass(generateAdapter = true)
data class ParsedScript(
    val title: String,
    val category: String,
    val comedyStyle: String,
    val duration: String,
    val format: String,
    val prompt: String,
    val scenes: List<ScriptScene>
)

@JsonClass(generateAdapter = true)
data class DailyScriptsContainer(
    val scripts: List<ParsedScript>
)
