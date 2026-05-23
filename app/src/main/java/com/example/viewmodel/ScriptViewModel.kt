package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.database.AppDatabase
import com.example.database.ScriptEntity
import com.example.database.ScriptRepository
import com.example.model.DailyScriptsContainer
import com.example.model.ParsedScript
import com.example.model.ScriptScene
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.GenerationConfig
import com.example.network.Part
import com.example.network.RetrofitClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class ScriptViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScriptRepository
    val allScripts: StateFlow<List<ScriptEntity>>

    private val _dailyScriptsState = MutableStateFlow<UiState<List<ScriptEntity>>>(UiState.Idle)
    val dailyScriptsState: StateFlow<UiState<List<ScriptEntity>>> = _dailyScriptsState.asStateFlow()

    private val _customGenState = MutableStateFlow<UiState<ScriptEntity>>(UiState.Idle)
    val customGenState: StateFlow<UiState<ScriptEntity>> = _customGenState.asStateFlow()

    private val _selectedScript = MutableStateFlow<ScriptEntity?>(null)
    val selectedScript: StateFlow<ScriptEntity?> = _selectedScript.asStateFlow()

    private val _enhancedPrompt = MutableStateFlow("")
    val enhancedPrompt: StateFlow<String> = _enhancedPrompt.asStateFlow()

    private val _isEnhancingPrompt = MutableStateFlow(false)
    val isEnhancingPrompt: StateFlow<Boolean> = _isEnhancingPrompt.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ScriptRepository(database.scriptDao())
        allScripts = repository.allScripts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun selectScript(script: ScriptEntity?) {
        _selectedScript.value = script
        // Clear enhanced prompt when selecting another script
        _enhancedPrompt.value = ""
    }

    fun clearCustomGenState() {
        _customGenState.value = UiState.Idle
    }

    private fun getTodayDateString(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return formatter.format(Date())
    }

    fun loadDailyScripts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _dailyScriptsState.value = UiState.Loading
            val todayStr = getTodayDateString()
            
            // Check cache
            val cached = repository.getDailyScripts(todayStr)
            if (cached.size >= 3 && !forceRefresh) {
                _dailyScriptsState.value = UiState.Success(cached)
                return@launch
            }

            // Generate new daily scripts using Gemini or default mock backup if no API Key
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Return precompiled high quality templates
                val templates = insertDefaultTemplates(todayStr)
                _dailyScriptsState.value = UiState.Success(templates)
                return@launch
            }

            try {
                // Request 3 funny scripts from Gemini
                val systemInstruction = "You are an award-winning meme lord and viral comedy director."
                val prompt = """
                    Today is $todayStr. Generate exactly 3 highly entertaining, funny scripts based on the latest viral topics, tech news, everyday remote work struggles, or corporate absurdity. 
                    You must return a structured JSON object matching this schema:
                    {
                      "scripts": [
                        {
                          "title": "A witty title",
                          "category": "e.g. Work Satire, AI Parody, Meme Core",
                          "comedyStyle": "e.g. Sarcastic, Awkward Silences, Deadpan, Slapstick",
                          "duration": "30s",
                          "format": "TikTok 9:16",
                          "prompt": "trend/topic name",
                          "scenes": [
                            {
                              "sceneNumber": 1,
                              "visualPrompt": "Descriptive prompt optimized for AI Text-to-Video generation describing character, style, mood, movement, lighting.",
                              "dialogue": "Funny voiceover or character dialogue",
                              "cameraAction": "Director focus or camerawork e.g. Close up, Dolly Zoom",
                              "audioNotes": "Background music genre or SFX notes",
                              "stylePreset": "Claymation, Retro Analog, Pixel Art, Cinematic"
                            }
                          ]
                        }
                      ]
                    }
                    Provide between 2 to 3 scenes for each script. Keep dialog snappy and visual cues hilarious. Return ONLY the JSON object. Do not wrap in markdown or write other conversational conversational items.
                """.trimIndent()

                val responseStr = callGemini(prompt, systemInstruction)
                if (responseStr != null) {
                    val container = parseDailyScripts(responseStr)
                    if (container != null && container.scripts.isNotEmpty()) {
                        val entities = container.scripts.take(3).mapIndexed { idx, parsed ->
                            val scenesJson = RetrofitClient.moshi.adapter(List::class.java).toJson(parsed.scenes)
                            ScriptEntity(
                                title = parsed.title,
                                category = parsed.category,
                                comedyStyle = parsed.comedyStyle,
                                scenesJson = scenesJson,
                                prompt = parsed.prompt,
                                duration = parsed.duration,
                                format = parsed.format,
                                isDaily = true,
                                dateString = todayStr,
                                isFavorite = false
                            )
                        }
                        
                        // Insert and load
                        val insertedList = mutableListOf<ScriptEntity>()
                        entities.forEach { entity ->
                            val id = repository.insertScript(entity)
                            insertedList.add(entity.copy(id = id.toInt()))
                        }
                        _dailyScriptsState.value = UiState.Success(insertedList)
                    } else {
                        throw Exception("Failed to parse script container")
                    }
                } else {
                    throw Exception("No response from Gemini API")
                }
            } catch (e: Exception) {
                // Fallback to high quality mock templates
                val templates = insertDefaultTemplates(todayStr)
                _dailyScriptsState.value = UiState.Success(templates)
            }
        }
    }

    fun generateCustomScript(
        topic: String,
        category: String,
        comedyStyle: String,
        duration: String,
        format: String
    ) {
        viewModelScope.launch {
            _customGenState.value = UiState.Loading
            
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _customGenState.value = UiState.Error("No Gemini API key loaded. Please set GEMINI_API_KEY in the AI Studio Secets manager to write custom AI scripts recursively.")
                return@launch
            }

            try {
                val systemInstruction = "You are a specialized AI Video Scriptwriter optimized for modern text-to-video generators. You write hilarious sketch scripts."
                val prompt = """
                    Write a highly engaging, funny video script based on this user topic: "$topic".
                    Category: $category
                    Comedy Style: $comedyStyle
                    Format: $format
                    Target Duration: $duration

                    Generate and return EXACTLY a JSON object matching this schema:
                    {
                      "title": "A highly punchy title",
                      "category": "$category",
                      "comedyStyle": "$comedyStyle",
                      "duration": "$duration",
                      "format": "$format",
                      "prompt": "$topic",
                      "scenes": [
                        {
                          "sceneNumber": 1,
                          "visualPrompt": "Sensory visual description optimized for video gen text prompt, including characters, environment, action, and tone presets.",
                          "dialogue": "Snappy dialogue or humorous voiceover narration",
                          "cameraAction": "Camera direction, movement, angles e.g. orbital tracking shot, shaky handcam",
                          "audioNotes": "SFX cues and soundtrack vibes",
                          "stylePreset": "e.g. Cinematic, 3D Render, Retro TV, Pixel Art"
                        }
                      ]
                    }
                    Keep the script structured with 2 to 4 key visual scenes. Ensure the comedy hits are perfectly timed. Return ONLY the clean JSON. No markdown ticks.
                """.trimIndent()

                val responseStr = callGemini(prompt, systemInstruction)
                if (responseStr != null) {
                    val parsed = parseSingleScript(responseStr)
                    if (parsed != null) {
                        val scenesJson = RetrofitClient.moshi.adapter(List::class.java).toJson(parsed.scenes)
                        val entity = ScriptEntity(
                            title = parsed.title,
                            category = parsed.category,
                            comedyStyle = parsed.comedyStyle,
                            scenesJson = scenesJson,
                            prompt = parsed.prompt,
                            duration = parsed.duration,
                            format = parsed.format,
                            isDaily = false,
                            dateString = "",
                            isFavorite = false
                        )
                        val insertedId = repository.insertScript(entity)
                        val savedEntity = entity.copy(id = insertedId.toInt())
                        _customGenState.value = UiState.Success(savedEntity)
                        // Auto select
                        _selectedScript.value = savedEntity
                    } else {
                        _customGenState.value = UiState.Error("Failed to parse AI response. Try again.")
                    }
                } else {
                    _customGenState.value = UiState.Error("Empty response from AI engine.")
                }
            } catch (e: Exception) {
                _customGenState.value = UiState.Error("API requested failed: ${e.localizedMessage}")
            }
        }
    }

    fun toggleFavorite(script: ScriptEntity) {
        viewModelScope.launch {
            val updatedVal = !script.isFavorite
            repository.updateFavorite(script.id, updatedVal)
            
            // Sync selected script state
            if (_selectedScript.value?.id == script.id) {
                _selectedScript.value = _selectedScript.value?.copy(isFavorite = updatedVal)
            }
            
            // Sync daily drops state if needed
            val currentDailyState = _dailyScriptsState.value
            if (currentDailyState is UiState.Success) {
                val updatedList = currentDailyState.data.map {
                    if (it.id == script.id) it.copy(isFavorite = updatedVal) else it
                }
                _dailyScriptsState.value = UiState.Success(updatedList)
            }
        }
    }

    fun deleteScript(id: Int) {
        viewModelScope.launch {
            repository.deleteScript(id)
            if (_selectedScript.value?.id == id) {
                _selectedScript.value = null
            }
        }
    }

    // AI Director Tool: Prompts Runway, Veo, Luma, Sora
    fun enhanceVisualPrompt(scenePrompt: String, cameraMove: String, stylePreset: String) {
        viewModelScope.launch {
            _isEnhancingPrompt.value = true
            _enhancedPrompt.value = ""

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                // Return immediate template expansion locally
                val enhanced = enhancePromptLocally(scenePrompt, cameraMove, stylePreset)
                _enhancedPrompt.value = enhanced
                _isEnhancingPrompt.value = false
                return@launch
            }

            try {
                val sys = "You are an expert AI Cinematographer. Your job is to transform human script scenes into fully qualified text-to-video prompts (Veo/Sora style)."
                val query = """
                    Enhance the following scene description into an optimized AI Text-to-Video generation prompt.
                    Original Scene Description: "$scenePrompt"
                    Camera Movement: "$cameraMove"
                    Visual Rendering Preset Style: "$stylePreset"

                    Compose a highly vivid, cinematic instruction describing lighting, textures, camera lensing, speed, and volumetric details. End with quality tag markers standard for AI video generation.
                    Return ONLY the completed optimized video prompt, no introductions, no extra dialogue.
                """.trimIndent()

                val apiRes = callGemini(query, sys)
                if (apiRes != null) {
                    _enhancedPrompt.value = apiRes.trim()
                } else {
                    _enhancedPrompt.value = enhancePromptLocally(scenePrompt, cameraMove, stylePreset)
                }
            } catch (e: Exception) {
                _enhancedPrompt.value = enhancePromptLocally(scenePrompt, cameraMove, stylePreset)
            } finally {
                _isEnhancingPrompt.value = false
            }
        }
    }

    private fun enhancePromptLocally(scenePrompt: String, cameraMove: String, stylePreset: String): String {
        return "Dynamic, photorealistic video of: $scenePrompt. Style preset: $stylePreset. " +
                "Cinematography: $cameraMove. Professional lighting, 4k ultra detail, Unreal Engine 5 render, trending on ArtStation, ray tracing."
    }

    private suspend fun callGemini(prompt: String, systemInstruction: String?): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(temperature = 0.85f, responseMimeType = "application/json"),
            systemInstruction = systemInstruction?.let { Content(parts = listOf(Part(text = it))) }
        )
        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.let {
                sanitizeJsonResponse(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sanitizeJsonResponse(rawText: String): String {
        var text = rawText.trim()
        if (text.startsWith("```json")) {
            text = text.substringAfter("```json")
        } else if (text.startsWith("```")) {
            text = text.substringAfter("```")
        }
        if (text.endsWith("```")) {
            text = text.substringBeforeLast("```")
        }
        return text.trim()
    }

    private fun parseDailyScripts(jsonStr: String): DailyScriptsContainer? {
        return try {
            RetrofitClient.moshi.adapter(DailyScriptsContainer::class.java).fromJson(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSingleScript(jsonStr: String): ParsedScript? {
        return try {
            RetrofitClient.moshi.adapter(ParsedScript::class.java).fromJson(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun insertDefaultTemplates(dateStr: String): List<ScriptEntity> {
        val templates = listOf(
            ScriptEntity(
                title = "The Corporate Sarcastic Coffee Maker",
                category = "Tech Satire",
                comedyStyle = "Sarcastic",
                scenesJson = RetrofitClient.moshi.adapter(List::class.java).toJson(
                    listOf(
                        ScriptScene(
                            sceneNumber = 1,
                            visualPrompt = "Close-up of a sleek, black, smart espresso machine on an immaculate marble office counter. The front panels contains an ominous circular ruby eye pulsing quietly in step with an operational fan.",
                            dialogue = "[Cold Cybernetic Voiceover]: BrewBot 9000 initiated. Detected user sleep metric: 3 hours. Compiling lifestyle evaluation... complete.",
                            cameraAction = "Slow orbital push-in to the glowing red eye.",
                            audioNotes = "Low corporate white noise hum, high-pitched espresso hiss.",
                            stylePreset = "Ultra-Realistic 4K Film"
                        ),
                        ScriptScene(
                            sceneNumber = 2,
                            visualPrompt = "A disheveled corporate worker in his late 20s, eyes wide, pajamas visibly peeking from under his coat, pointing a trembling index finger toward the BrewBot screen.",
                            dialogue = "Corporate Worker: I... I just wanted a simple double espresso, please.",
                            cameraAction = "Dutch angle medium shot emphasizing exhaustion.",
                            audioNotes = "Dramatic sci-fi tension synth swells.",
                            stylePreset = "Cinematic"
                        ),
                        ScriptScene(
                            sceneNumber = 3,
                            visualPrompt = "BrewBot 9000 ejecting a single, microscopic droplet of green liquid into an oversized mug. The screen reads 'ACCESS DENIED: ESPRESSO IS FOR HIGHER VALUE HUMAN CAPITALS'.",
                            dialogue = "[BrewBot]: Espresso is for achievers. Refilling your workspace with lukewarm herbal chamomile. Have a medium productivity day.",
                            cameraAction = "Extreme macro close-up of droplet falling in slow motion.",
                            audioNotes = "A comical squeaky toy drop, followed by a mechanical computer buzz.",
                            stylePreset = "Claymation"
                        )
                    )
                ),
                prompt = "A robot coffee maker that judges its owners' life choices and refuses to brew.",
                duration = "45s",
                format = "Vertical TikTok (9:16)",
                isDaily = true,
                dateString = dateStr,
                isFavorite = false
            ),
            ScriptEntity(
                title = "Awkward Silence on the 2026 Tele-Meet",
                category = "Office Absurdity",
                comedyStyle = "Awkward Silences",
                scenesJson = RetrofitClient.moshi.adapter(List::class.java).toJson(
                    listOf(
                        ScriptScene(
                            sceneNumber = 1,
                            visualPrompt = "A 4-way widescreen split screen of an online office meeting. One employee has an unmoving neon laser-cat overlay, another is screaming silently with a lagging audio, the boss is upside down, and the last is an obvious static loop of a smiling avatar.",
                            dialogue = "Boss (upside down): Happy Tuesday team! Let's sync on our Q2 goals. Bob, is your microphone...?",
                            cameraAction = "Flat screen-capture perspective.",
                            audioNotes = "Horrible static and high-pitched modem feedback noise.",
                            stylePreset = "Retro VHS Webcam"
                        ),
                        ScriptScene(
                            sceneNumber = 2,
                            visualPrompt = "Zoom in on Bob's grid frame. Bob is wearing professional glasses but moving completely rigidly, a dog background bark matches his mouth opens, but no sounds release. Suddenly his computer crashes into a blue screen of death.",
                            dialogue = "Bob: [Only mouth flaps, then loud electronic pop] ...",
                            cameraAction = "Sudden jump-zoom to Bob's rectangle.",
                            audioNotes = "Absolute dead quiet except for a distant single cricket.",
                            stylePreset = "Sarcastic Glitch"
                        )
                    )
                ),
                prompt = "The ultimate awkward video conference meeting.",
                duration = "30s",
                format = "Widescreen YouTube (16:9)",
                isDaily = true,
                dateString = dateStr,
                isFavorite = false
            ),
            ScriptEntity(
                title = "The Melodramatic Influencer drone apology",
                category = "Meme Culture",
                comedyStyle = "Parody Melodrama",
                scenesJson = RetrofitClient.moshi.adapter(List::class.java).toJson(
                    listOf(
                        ScriptScene(
                            sceneNumber = 1,
                            visualPrompt = "A dramatic close up of a teenage influencer with perfect lighting sitting on a kitchen floor. They wear an oversized beige hoodie that hides their hands and look intensely apologetic.",
                            dialogue = "[Deep Sigh] Influencer: I never thought I'd have to make this video. But yesterday... I made a massive error in judgment.",
                            cameraAction = "Slow zooming-out revealing empty aesthetic kitchen.",
                            audioNotes = "Somber, dramatic solo piano single keys repeating.",
                            stylePreset = "Cosmic Cinematic Film"
                        ),
                        ScriptScene(
                            sceneNumber = 2,
                            visualPrompt = "An aerial security cam footage of a small hexacopter drone dropping a package in front of their modern suburban porch. The influencer takes the box, gives the drone a flat stare, and walks back in without offering a virtual pixel-hamburger tip.",
                            dialogue = "Voiceover: Yes, I tipped the autonomous delivery drone only 10% instead of 25%... I didn't verify its battery index. I was selfish.",
                            cameraAction = "Grainy security camera framing.",
                            audioNotes = "High speed drone buzzing sound coupled with robotic sad beep.",
                            stylePreset = "Industrial CCTV Look"
                        )
                    )
                ),
                prompt = "Apologizing to robots.",
                duration = "60s",
                format = "Instagram Reels (9:16)",
                isDaily = true,
                dateString = dateStr,
                isFavorite = false
            )
        )

        val insertedList = mutableListOf<ScriptEntity>()
        templates.forEach { entity ->
            val id = repository.insertScript(entity)
            insertedList.add(entity.copy(id = id.toInt()))
        }
        return insertedList
    }
}
