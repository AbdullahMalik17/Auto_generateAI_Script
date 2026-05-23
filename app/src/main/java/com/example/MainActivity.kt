package com.example

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.database.ScriptEntity
import com.example.model.ScriptScene
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ScriptViewModel
import com.example.viewmodel.UiState
import com.squareup.moshi.Moshi
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Native engine for speech preview
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(
                    onSpeak = { text ->
                        if (isTtsReady) {
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                        } else {
                            Toast.makeText(this, "Speech engine loading...", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onSpeak: (String) -> Unit,
    viewModel: ScriptViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val allScripts by viewModel.allScripts.collectAsStateWithLifecycle()
    val dailyState by viewModel.dailyScriptsState.collectAsStateWithLifecycle()
    val customGenState by viewModel.customGenState.collectAsStateWithLifecycle()
    val selectedScript by viewModel.selectedScript.collectAsStateWithLifecycle()
    val enhancedPrompt by viewModel.enhancedPrompt.collectAsStateWithLifecycle()
    val isEnhancingPrompt by viewModel.isEnhancingPrompt.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Daily drops, 1: AI Lab, 2: Script Vault

    // Automatically load daily scripts on start
    LaunchedEffect(Unit) {
        viewModel.loadDailyScripts()
    }

    // Helper Moshi to decode scenesJson for display
    val moshi = remember { Moshi.Builder().build() }
    val listType = remember { com.squareup.moshi.Types.newParameterizedType(List::class.java, ScriptScene::class.java) }
    val scenesAdapter = remember { moshi.adapter<List<ScriptScene>>(listType) }

    fun getScenesList(json: String): List<ScriptScene> {
        return try {
            scenesAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Daily Dispatch") },
                    label = { Text("Daily Drops", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Lab") },
                    label = { Text("Script Lab", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.FolderZip, contentDescription = "Script Vault") },
                    label = { Text("My Vault", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        indicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            Color(0xFF04060C)
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("glowing_logo")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "AI FUNNY SCRIPT",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "DIRECTOR BOARD STUDIO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // API Key Alert indicator
                val isDemoKey = BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY"
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDemoKey) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (isDemoKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary),
                    modifier = Modifier.clickable {
                        Toast.makeText(
                            context,
                            if (isDemoKey) "Offline Mock Mode - Add GEMINI_API_KEY in AI Studio's Secrets Manager for infinite AI scripts!"
                            else "Gemini API Active!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = if (isDemoKey) Color(0xFFFF2A6D) else Color(0xFF00F0FF),
                                        radius = size.minDimension / 2
                                    )
                                }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDemoKey) "DEMO MODE" else "AI ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isDemoKey) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Warning banner if DEMO/Mock key
            if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Demo Mode Information",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "No Gemini API key detected. Using high-fidelity comedy drops. To unlock custom on-demand scripts, enter a real GEMINI_API_KEY in the Secrets side panel.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 1.dp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Main Contents switching with tabs
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> DailyDropsView(
                        dailyState = dailyState,
                        onRefresh = { viewModel.loadDailyScripts(forceRefresh = true) },
                        onSelectScript = { script -> viewModel.selectScript(script) }
                    )
                    1 -> ScriptLabView(
                        customGenState = customGenState,
                        onGenerate = { topic, category, style, duration, format ->
                            viewModel.generateCustomScript(topic, category, style, duration, format)
                        },
                        onClearState = { viewModel.clearCustomGenState() }
                    )
                    2 -> ScriptVaultView(
                        allScripts = allScripts,
                        onSelectScript = { script -> viewModel.selectScript(script) }
                    )
                }
            }
        }
    }

    // Full Screen Script Details / Movie Editor Workspace overlays when selected
    selectedScript?.let { activeScript ->
        Dialog(
            onDismissRequest = { viewModel.selectScript(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Title Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.selectScript(null) },
                                    modifier = Modifier.background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(8.dp)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = activeScript.title,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${activeScript.category} • ${activeScript.comedyStyle}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = activeScript.format,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Star favorite & delete
                            Row {
                                IconButton(
                                    onClick = { viewModel.toggleFavorite(activeScript) },
                                    modifier = Modifier.testTag("script_favorite_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (activeScript.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (activeScript.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.deleteScript(activeScript.id)
                                        viewModel.selectScript(null)
                                        Toast.makeText(context, "Script deleted from vault", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("delete_script_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Script",
                                        tint = Color.Red.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )

                        // Studio Layout
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                // Prompt tag
                                SectionLabel(text = "Prompt Core Topic")
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = activeScript.prompt.ifBlank { "Unspecified Topic" },
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                        modifier = Modifier.padding(12.dp),
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                SectionLabel(text = "Video Director Storyboard")
                                Text(
                                    text = "Click on a scene to open the AI Video Prompting Forge & TTS sound preview.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            val scenes = getScenesList(activeScript.scenesJson)
                            if (scenes.isEmpty()) {
                                item {
                                    Text(
                                        text = "No scenes parsed successfully.",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            } else {
                                items(scenes) { scene ->
                                    var isExpanded by remember { mutableStateOf(false) }
                                    var selectedCamAction by remember { mutableStateOf(scene.cameraAction) }
                                    var selectedRenderStyle by remember { mutableStateOf(scene.stylePreset) }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .border(
                                                width = 1.dp,
                                                color = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(14.dp)
                                            ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .clickable { isExpanded = !isExpanded }
                                                .padding(14.dp)
                                        ) {
                                            // Scene Card Header
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(24.dp)
                                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = scene.sceneNumber.toString(),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(
                                                        text = "Storyboard Scene",
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onBackground
                                                    )
                                                }

                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Expand info",
                                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Visual Frame preview slot! Immersive camera recording frame!
                                            val isVertical = activeScript.format.contains("9:16") || activeScript.format.lowercase().contains("vertical")
                                            val ratio = if (isVertical) 0.65f else 1.8f
                                            
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(ratio)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.Black)
                                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                            ) {
                                                // Graticule overlays
                                                Canvas(modifier = Modifier.fillMaxSize()) {
                                                    // Draw aspect ratio bounding bracket marks
                                                    val w = size.width
                                                    val h = size.height
                                                    val len = 12.dp.toPx()
                                                    
                                                    // Top-Left bracket
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(len, len), Offset(len * 2, len), strokeWidth = 2f)
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(len, len), Offset(len, len * 2), strokeWidth = 2f)
                                                    // Top-Right bracket
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(w - len, len), Offset(w - len * 2, len), strokeWidth = 2f)
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(w - len, len), Offset(w - len, len * 2), strokeWidth = 2f)
                                                    // Bottom-Left bracket
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(len, h - len), Offset(len * 2, h - len), strokeWidth = 2f)
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(len, h - len), Offset(len, h - len * 2), strokeWidth = 2f)
                                                    // Bottom-Right bracket
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(w - len, h - len), Offset(w - len * 2, h - len), strokeWidth = 2f)
                                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(w - len, h - len), Offset(w - len, h - len * 2), strokeWidth = 2f)
                                                }

                                                // Record details
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp)
                                                        .align(Alignment.TopStart),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(Color.Red, RoundedCornerShape(100.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "REC",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.Red,
                                                            letterSpacing = 1.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = "CAM PRESET • " + selectedRenderStyle.uppercase(),
                                                        fontSize = 8.sp,
                                                        color = Color.White.copy(alpha = 0.5f),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                // Display visually descriptive overlay text inside camera viewfinder
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp)
                                                        .align(Alignment.BottomStart)
                                                ) {
                                                    Text(
                                                        text = "[AI VIDEO GENERATOR PROMPT INPUT]",
                                                        fontSize = 8.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = scene.visualPrompt,
                                                        fontSize = 11.sp,
                                                        color = Color.White,
                                                        maxLines = if (isExpanded) 10 else 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Comedic Dialogue Speak Section
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            // Strip speaker names if present
                                                            val cleanDialogue = scene.dialogue.replace(Regex("^[a-zA-Z0-9\\s]+:"), "")
                                                            onSpeak(cleanDialogue)
                                                        },
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(6.dp))
                                                            .testTag("speak_scene_dialogue_btn")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.VolumeUp,
                                                            contentDescription = "Speak Dialogue",
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Dialogue / Voiceover",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Text(
                                                            text = scene.dialogue,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }
                                            }

                                            // Expanded AI Video Prompter Forge Tools
                                            AnimatedVisibility(
                                                visible = isExpanded,
                                                enter = expandVertically() + fadeIn(),
                                                exit = shrinkVertically() + fadeOut()
                                            ) {
                                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                                    Spacer(modifier = Modifier.height(12.dp))

                                                    Text(
                                                        text = "AI VIDEO PROMPT GENERATOR FORGE",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        letterSpacing = 1.sp
                                                    )
                                                    Text(
                                                        text = "Transform script visuals into elite, production-ready prompts suited for systems like Veo, Sora, or Gen-3.",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                                        modifier = Modifier.padding(bottom = 12.dp)
                                                    )

                                                    // Presets Row 1: Cinematic Movement Selector
                                                    Text(
                                                        text = "Select Camera Framing Preset:",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                                    )
                                                    val camOptions = listOf(
                                                        "Cinematic Slow Zoom-In",
                                                        "Macro Lens Extreme Close Up",
                                                        "Dolly Zoom/Vertigo Effect",
                                                        "360 Orbital Space Pan",
                                                        "Low Angle Extreme Tracking Shot"
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        LazyRowOfChips(
                                                            items = camOptions,
                                                            selectedItem = selectedCamAction,
                                                            onSelectedChanged = { selectedCamAction = it }
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    // Presets Row 2: Render style Preset
                                                    Text(
                                                        text = "Select Visual Art Presets:",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                                    )
                                                    val renderPresets = listOf(
                                                        "Photorealistic 4K Unreal Render",
                                                        "Claymation Handcrafted Style",
                                                        "Retro 1990s VHS Webcam",
                                                        "3D Pixar CGI Animation",
                                                        "Sci-fi Cyberpunk Hologram"
                                                    )
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        LazyRowOfChips(
                                                            items = renderPresets,
                                                            selectedItem = selectedRenderStyle,
                                                            onSelectedChanged = { selectedRenderStyle = it }
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(16.dp))

                                                    // Generate Action Button
                                                    Button(
                                                        onClick = {
                                                            viewModel.enhanceVisualPrompt(scene.visualPrompt, selectedCamAction, selectedRenderStyle)
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("enhance_prompt_btn"),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                        shape = RoundedCornerShape(10.dp)
                                                    ) {
                                                        if (isEnhancingPrompt) {
                                                            CircularProgressIndicator(
                                                                modifier = Modifier.size(18.dp),
                                                                color = MaterialTheme.colorScheme.onPrimary,
                                                                strokeWidth = 2.dp
                                                            )
                                                        } else {
                                                            Icon(
                                                                imageVector = Icons.Default.FlashOn,
                                                                contentDescription = "Enhance icon",
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "FORGE OPTIMIZED VIDEO PROMPT",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                        }
                                                    }

                                                    // Show Copiable Result code panel
                                                    if (enhancedPrompt.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Surface(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                                            color = Color.Black,
                                                            shape = RoundedCornerShape(10.dp)
                                                        ) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "ENGINE OPTIMIZED AI PROMPT",
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Black,
                                                                        color = MaterialTheme.colorScheme.tertiary,
                                                                        fontFamily = FontFamily.Monospace
                                                                    )
                                                                    IconButton(
                                                                        onClick = {
                                                                            clipboardManager.setText(AnnotatedString(enhancedPrompt))
                                                                            Toast.makeText(context, "Copied Video Prompt to clipboard!", Toast.LENGTH_SHORT).show()
                                                                        },
                                                                        modifier = Modifier
                                                                            .size(28.dp)
                                                                            .testTag("copy_prompt_btn")
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.ContentCopy,
                                                                            contentDescription = "Copy prompt code",
                                                                            tint = MaterialTheme.colorScheme.tertiary,
                                                                            modifier = Modifier.size(14.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = enhancedPrompt,
                                                                    fontSize = 12.sp,
                                                                    color = Color.White,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    lineHeight = 15.sp
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    // Audio and camera hints
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        DirectorChip(
                                                            label = "Audio SFX Cue",
                                                            content = scene.audioNotes,
                                                            icon = Icons.Default.AudioFile
                                                        )
                                                        DirectorChip(
                                                            label = "Video Cam Presets",
                                                            content = scene.cameraAction,
                                                            icon = Icons.Default.PhotoCamera
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            item { 
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
    }
}

@Composable
fun LazyRowOfChips(
    items: List<String>,
    selectedItem: String,
    onSelectedChanged: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            val isSelected = selectedItem == item
            Surface(
                modifier = Modifier
                    .padding(end = 6.dp, top = 2.dp, bottom = 2.dp)
                    .clickable { onSelectedChanged(item) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Text(
                    text = item,
                    fontSize = 10.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun DirectorChip(
    label: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = content,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

// ------------------------ SUB SCREEN VIEWS ------------------------

@Composable
fun DailyDropsView(
    dailyState: UiState<List<ScriptEntity>>,
    onRefresh: () -> Unit,
    onSelectScript: (ScriptEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TODAY'S DAILY COMEDY CORES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Curated automatically to inspire viral jokes",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .testTag("daily_refresh_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh lists",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (dailyState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Gemini is scanning comedy vectors...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            is UiState.Success -> {
                val scripts = dailyState.data
                if (scripts.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No curated drops found for today. Tap refresh to launch compilation.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(scripts) { script ->
                            ScriptListItem(script = script, onClick = { onSelectScript(script) })
                        }
                    }
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Curator experienced an anomaly: ${(dailyState as UiState.Error).message}",
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScriptLabView(
    customGenState: UiState<ScriptEntity>,
    onGenerate: (String, String, String, String, String) -> Unit,
    onClearState: () -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Tech Parody") }
    var selectedStyle by remember { mutableStateOf("Sarcastic") }
    var selectedDuration by remember { mutableStateOf("30s") }
    var selectedFormat by remember { mutableStateOf("Vertical TikTok (9:16)") }

    val categories = listOf("Tech Parody", "Office Realities", "Internet Meme", "Sci-fi Satire", "Daily Absurd")
    val styles = listOf("Sarcastic", "Awkward Silences", "Deadpan Snark", "Meme Parody", "Melodrama")
    val durations = listOf("15s", "30s", "45s", "60s")
    val formats = listOf("Vertical TikTok (9:16)", "Widescreen YouTube (16:9)", "Square Post (1:1)")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Text(
                text = "COMMISSION CUSTOM SCRIPT",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Structure funny visual templates and video cues about custom ideas",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Prompt field
            Text(
                text = "WHAT'S THE FUNNY CONCEPT?",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                placeholder = {
                    Text(
                        text = "e.g. A cat is trying to file taxes on an iPad but gets distracted by dust motes",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prompt_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pre-built Chip Grid: Categories
            SectionLabel(text = "Category Topic")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    val isSel = cat == selectedCategory
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            selectedBorderWidth = 1.dp,
                            enabled = true,
                            selected = isSel
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Prebuilt Chip Grid: Styles
            SectionLabel(text = "Comedy Delivery Style Theme")
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                styles.forEach { style ->
                    val isSel = style == selectedStyle
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedStyle = style },
                        label = { Text(style, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            selectedBorderWidth = 1.dp,
                            enabled = true,
                            selected = isSel
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SectionLabel(text = "Target Duration")
                    LazyRowOfChips(
                        items = durations,
                        selectedItem = selectedDuration,
                        onSelectedChanged = { selectedDuration = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Formats Selector Row
            SectionLabel(text = "Video Camera Layout & Aspect Ratio")
            LazyRowOfChips(
                items = formats,
                selectedItem = selectedFormat,
                onSelectedChanged = { selectedFormat = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Trigger generation
            Button(
                onClick = {
                    if (topic.isNotBlank()) {
                        onGenerate(topic, selectedCategory, selectedStyle, selectedDuration, selectedFormat)
                    } else {
                        onGenerate("Funny story about random encounter", selectedCategory, selectedStyle, selectedDuration, selectedFormat)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_script_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Submit Spark"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "WRITE AI COMEDY SCRIPT DRAFT",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // State indicators
            when (customGenState) {
                is UiState.Loading -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Gemini is building script structures...",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Formulating puns, scene directions, and camera frames",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
                is UiState.Success -> {
                    // Script was successfully loaded and opened! Reset state so form is fresh
                    LaunchedEffect(Unit) {
                        onClearState()
                    }
                }
                is UiState.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error icon",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI Writer Error",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = (customGenState as UiState.Error).message,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun ScriptVaultView(
    allScripts: List<ScriptEntity>,
    onSelectScript: (ScriptEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        var onlyFavorites by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "VAULTED REPOSITORY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "All drafts saved locally in your database cache",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            // Favorites filter chip
            FilterChip(
                selected = onlyFavorites,
                onClick = { onlyFavorites = !onlyFavorites },
                label = { Text("Starred Items Only", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                leadingIcon = {
                    Icon(
                        imageVector = if (onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Filter icons",
                        modifier = Modifier.size(12.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    selectedLabelColor = MaterialTheme.colorScheme.tertiary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.tertiary
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val filtered = if (onlyFavorites) allScripts.filter { it.isFavorite } else allScripts

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Empty Folder",
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (onlyFavorites) "No starred scripts in vault yet." else "Vault is completely silent.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Generate custom stories or select daily curated concepts to persist drafts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered) { script ->
                    ScriptListItem(script = script, onClick = { onSelectScript(script) })
                }
            }
        }
    }
}

@Composable
fun ScriptListItem(
    script: ScriptEntity,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("task_item_card"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Format pill/slate on left side representing video orientation
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val isVert = script.format.contains("9:16") || script.format.lowercase().contains("vertical")
                Icon(
                    imageVector = if (isVert) Icons.Default.PhoneAndroid else Icons.Default.Tv,
                    contentDescription = "Video aspect ratio style icon",
                    tint = if (isVert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = script.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (script.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Starred item icon",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = script.comedyStyle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    )
                    Text(
                        text = script.category,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                    if (script.isDaily) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "DAILY CURATED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Open Director Script Editor",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
            }
        }
    }
}
