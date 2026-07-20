package com.charles.ollama.client.ui.chat

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.domain.model.ChatMessage
import com.charles.ollama.client.ui.components.ErrorDialog
import com.charles.ollama.client.ui.components.LoadingIndicator
import com.charles.ollama.client.ui.components.MessageBubble
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.ui.components.NativeAdCard
import com.charles.ollama.client.ui.localization.translated
import com.charles.ollama.client.ui.models.displayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import android.util.Base64
import com.charles.ollama.client.util.ImageCompressionHelper
import com.charles.ollama.client.util.PerformanceMonitor
import com.charles.ollama.client.util.TextToSpeechHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToPromptLibrary: (Long) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    // Performance monitoring for screen rendering
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("ChatScreen") }
    LaunchedEffect(Unit) {
        PerformanceMonitor.addAttribute(screenTrace, "thread_id", threadId.toString())
    }
    val ttsAppContext = LocalContext.current.applicationContext
    val ttsHelper = remember(ttsAppContext) { TextToSpeechHelper(context = ttsAppContext) }
    DisposableEffect(Unit) {
        onDispose {
            PerformanceMonitor.stopTrace(screenTrace)
            ttsHelper.shutdown()
        }
    }
    
    val messages by viewModel.messages.collectAsState()
    val thread by viewModel.thread.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val isVisionModel by viewModel.isVisionModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val searchActive by viewModel.searchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val matchIndices by viewModel.matchMessageIndices.collectAsState()
    val currentMatch by viewModel.currentMatchIndex.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) } // Base64 encoded images
    var showModelSelector by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showTitleEditDialog by remember { mutableStateOf(false) }
    // Hands-free voice mode: speak -> auto-send -> read reply aloud -> listen again.
    var voiceMode by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speakPrompt = translated("Speak your message")
    val speechUnavailable = translated("Speech recognizer not available on this device")

    // Ask happy users to rate the app after a few successful replies (Play In-App Review API).
    val shouldRequestReview by viewModel.shouldRequestReview.collectAsState()
    LaunchedEffect(shouldRequestReview) {
        if (shouldRequestReview) {
            (context as? Activity)?.let { activity ->
                viewModel.launchReviewFlow(activity)
            }
            viewModel.consumeReviewRequest()
        }
    }

    // Shared send action so both the Send button and voice mode use one path.
    val performSend: (String) -> Unit = { text ->
        val canSendNow = (text.isNotBlank() || selectedImages.isNotEmpty()) && selectedModel != null
        if (canSendNow) {
            val imagesToSend = if (selectedImages.isNotEmpty()) selectedImages else null
            viewModel.sendMessage(text, imagesToSend)
            messageText = ""
            selectedImages = emptyList()
        }
    }

    // System speech-to-text launcher (no library, no manifest permission needed —
    // the dedicated recognizer activity owns the mic capture).
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                if (voiceMode) {
                    // Hands-free: send what was heard immediately.
                    performSend(spoken)
                } else {
                    messageText = if (messageText.isBlank()) spoken
                    else messageText.trimEnd() + " " + spoken
                }
            }
        }
    }

    // Launches the system speech recognizer; reused by the mic button and voice mode.
    val startListening: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, speakPrompt)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                speechUnavailable,
                Toast.LENGTH_SHORT
            ).show()
            voiceMode = false
        }
    }

    // When a reply finishes while voice mode is on, read it aloud then listen again.
    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading, voiceMode) {
        if (wasLoading && !isLoading && voiceMode) {
            val lastAssistant = messages.lastOrNull { it.role == "assistant" }?.content?.trim()
            if (!lastAssistant.isNullOrEmpty()) {
                ttsHelper.speak(lastAssistant)
            }
            kotlinx.coroutines.delay(600)
            if (voiceMode) startListening()
        }
        wasLoading = isLoading
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                    inputStream?.use { stream ->
                        val bytes = stream.readBytes()
                        // Compress image before encoding to prevent SQLite CursorWindow overflow
                        val base64 = withContext(Dispatchers.Default) {
                            PerformanceMonitor.measureSuspend("image_compress_and_encode") {
                                try {
                                    ImageCompressionHelper.compressAndEncodeImage(bytes)
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatScreen", "Error compressing image, using original", e)
                                    // Fallback to original if compression fails
                                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                                }
                            }
                        }
                        selectedImages = selectedImages + base64
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "Error loading image", e)
                }
            }
        }
    }
    
    val listState = rememberLazyListState()
    
    LaunchedEffect(threadId) {
        viewModel.setThreadId(threadId)
    }
    
    // Pick a stable interleaved native-ad slot once per session so the ad
    // doesn't jump as new messages stream in.
    val nativeAdAfter = rememberSaveable { (1..3).random() }
    val chatRows = remember(messages, nativeAdAfter) {
        buildChatRows(messages, nativeAdAfter)
    }

    LaunchedEffect(messages.size) {
        // Don't yank the user away from a search match while they're navigating it.
        if (messages.isNotEmpty() && !searchActive) {
            scope.launch {
                listState.animateScrollToItem(chatRows.lastIndex.coerceAtLeast(0))
            }
        }
    }

    // Map a message index to a chat row index (the ad row is interleaved after `nativeAdAfter` messages).
    fun messageIndexToRow(messageIndex: Int): Int {
        return if (messages.size > nativeAdAfter && messageIndex >= nativeAdAfter) messageIndex + 1
        else messageIndex
    }

    LaunchedEffect(searchActive, currentMatch, matchIndices) {
        if (!searchActive) return@LaunchedEffect
        val matches = matchIndices
        if (matches.isEmpty()) return@LaunchedEffect
        val targetMessageIndex = matches[currentMatch.coerceIn(0, matches.lastIndex)]
        listState.animateScrollToItem(messageIndexToRow(targetMessageIndex))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = viewModel::updateSearchQuery,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(translated("Search in chat")) },
                            singleLine = true,
                            trailingIcon = {
                                if (matchIndices.isNotEmpty()) {
                                    Text(
                                        text = "${currentMatch + 1}/${matchIndices.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                } else if (searchQuery.isNotBlank()) {
                                    Text(
                                        text = "0/0",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        )
                    } else {
                        Column {
                            Text(thread?.title ?: translated("Chat"))
                            selectedModel?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) viewModel.setSearchActive(false) else onNavigateBack()
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = translated(if (searchActive) "Close search" else "Back")
                        )
                    }
                },
                actions = {
                    if (searchActive) {
                        IconButton(
                            onClick = viewModel::previousMatch,
                            enabled = matchIndices.isNotEmpty()
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = translated("Previous match"))
                        }
                        IconButton(
                            onClick = viewModel::nextMatch,
                            enabled = matchIndices.isNotEmpty()
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = translated("Next match"))
                        }
                    } else {
                        IconButton(onClick = {
                            voiceMode = !voiceMode
                            if (voiceMode) startListening() else ttsHelper.stop()
                        }) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = translated(if (voiceMode) "Turn off voice mode" else "Turn on voice mode"),
                                tint = if (voiceMode) MaterialTheme.colorScheme.primary
                                else LocalContentColor.current
                            )
                        }
                        IconButton(onClick = { viewModel.setSearchActive(true) }) {
                            Icon(Icons.Default.Search, contentDescription = translated("Search this chat"))
                        }
                        IconButton(onClick = { viewModel.shareCurrentThread() }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = translated("Share chat as Markdown")
                            )
                        }
                        IconButton(onClick = { showChatSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = translated("Chat Settings")
                            )
                        }
                        IconButton(onClick = { showModelSelector = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = translated("Select Model")
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BannerAd()
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Lift the input above the soft keyboard. consumeWindowInsets tells
                // imePadding the scaffold padding (e.g. the banner) is already applied,
                // so the input sits directly on top of the keyboard with no gap.
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(chatRows, key = ChatRow::key) { row ->
                    when (row) {
                        is ChatRow.Msg -> {
                            val showThinking by viewModel.showThinking.collectAsState()
                            MessageBubble(
                                message = row.message,
                                showThinking = showThinking,
                                onLoadImages = { messageId -> viewModel.loadMessageImages(messageId) },
                                highlightQuery = if (searchActive) searchQuery else null,
                                onShare = { messageId -> viewModel.shareMessageById(messageId) },
                                onDelete = { messageId -> viewModel.deleteSingleMessage(messageId) },
                                onReadAloud = { text -> ttsHelper.speak(text) },
                                onRegenerate = { messageId -> viewModel.regenerateAssistant(messageId) },
                                onEditAndResend = { messageId, newContent ->
                                    viewModel.editAndResend(messageId, newContent)
                                },
                                onReport = { messageId -> viewModel.reportMessage(messageId) },
                            )
                        }
                        is ChatRow.Ad -> NativeAdCard()
                    }
                }
                
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
            
            // Show selected images
            if (selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedImages.forEachIndexed { index, base64Image ->
                        Box(modifier = Modifier.size(80.dp)) {
                            val bitmap = remember(base64Image) {
                                try {
                                    val imageBytes = Base64.decode(base64Image, Base64.NO_WRAP)
                                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Selected image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedImages = selectedImages.filterIndexed { i, _ -> i != index }
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = translated("Remove image"),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
            // AI-content disclaimer (Google Play AI-Generated Content policy).
            Text(
                text = "AI responses may be inaccurate — verify important information. Long-press the ⋮ menu on a reply to report it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Image picker button (only show for vision models)
                if (isVisionModel) {
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        enabled = selectedModel != null // Allow selecting images even while loading
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Add image")
                    }
                }
                IconButton(
                    onClick = { startListening() },
                    enabled = selectedModel != null
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Voice input")
                }
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(translated("Type a message...")) },
                    enabled = selectedModel != null, // Allow typing even while loading
                    singleLine = false,
                    maxLines = 4
                )
                val canSend = (messageText.isNotBlank() || selectedImages.isNotEmpty()) && selectedModel != null
                if (isLoading) {
                    // While a reply is streaming, the action button stops generation.
                    FloatingActionButton(
                        onClick = { viewModel.stopGeneration() },
                        modifier = Modifier.size(56.dp),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = translated("Stop generating"))
                    }
                } else {
                    FloatingActionButton(
                        onClick = { performSend(messageText) },
                        modifier = Modifier.size(56.dp),
                        containerColor = if (canSend)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (canSend)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(Icons.Default.Send, contentDescription = translated("Send"))
                    }
                }
            }
        }
    }
    
    if (showModelSelector) {
        ModelSelectorDialog(
            currentModel = selectedModel,
            availableModels = availableModels,
            isLoading = isLoadingModels,
            onDismiss = { showModelSelector = false },
            onSelect = { model ->
                viewModel.setModel(model)
                showModelSelector = false
            },
            onRefresh = { viewModel.loadAvailableModels() }
        )
    }
    
    if (showChatSettings) {
        val showThinking by viewModel.showThinking.collectAsState()
        ChatSettingsDialog(
            thread = thread,
            showThinking = showThinking,
            onDismiss = { showChatSettings = false },
            onBrowseLibrary = {
                showChatSettings = false
                onNavigateToPromptLibrary(threadId)
            },
            onStreamEnabledChanged = { enabled ->
                viewModel.updateStreamEnabled(enabled)
            },
            onSystemPromptChanged = { prompt ->
                viewModel.updateSystemPrompt(prompt)
            },
            onVibrationEnabledChanged = { enabled ->
                viewModel.updateVibrationEnabled(enabled)
            },
            onShowThinkingChanged = { show ->
                viewModel.setShowThinking(show)
            },
            onModelParamsChanged = { temperature, topP, topK, numCtx, seed ->
                viewModel.updateModelParams(temperature, topP, topK, numCtx, seed)
            }
        )
    }
    
    error?.let {
        ErrorDialog(
            message = it,
            onDismiss = viewModel::clearError
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsDialog(
    thread: com.charles.ollama.client.data.database.entity.ChatThreadEntity?,
    showThinking: Boolean,
    onDismiss: () -> Unit,
    onStreamEnabledChanged: (Boolean) -> Unit,
    onSystemPromptChanged: (String) -> Unit,
    onVibrationEnabledChanged: (Boolean) -> Unit,
    onShowThinkingChanged: (Boolean) -> Unit,
    onModelParamsChanged: (Float?, Float?, Int?, Int?, Int?) -> Unit = { _, _, _, _, _ -> },
    onBrowseLibrary: () -> Unit = {}
) {
    var streamEnabled by remember { mutableStateOf(thread?.streamEnabled ?: true) }
    var systemPrompt by remember { mutableStateOf(thread?.systemPrompt ?: "") }
    var vibrationEnabled by remember { mutableStateOf(thread?.vibrationEnabled ?: true) }
    var temperatureText by remember { mutableStateOf(thread?.temperature?.toString() ?: "") }
    var topPText by remember { mutableStateOf(thread?.topP?.toString() ?: "") }
    var topKText by remember { mutableStateOf(thread?.topK?.toString() ?: "") }
    var numCtxText by remember { mutableStateOf(thread?.numCtx?.toString() ?: "") }
    var seedText by remember { mutableStateOf(thread?.seed?.toString() ?: "") }

    LaunchedEffect(thread) {
        streamEnabled = thread?.streamEnabled ?: true
        systemPrompt = thread?.systemPrompt ?: ""
        vibrationEnabled = thread?.vibrationEnabled ?: true
        temperatureText = thread?.temperature?.toString() ?: ""
        topPText = thread?.topP?.toString() ?: ""
        topKText = thread?.topK?.toString() ?: ""
        numCtxText = thread?.numCtx?.toString() ?: ""
        seedText = thread?.seed?.toString() ?: ""
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(translated("Chat Settings")) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Streaming toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translated("Stream Responses"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = translated("Enable streaming to see AI responses in real-time"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = streamEnabled,
                        onCheckedChange = { 
                            streamEnabled = it
                            onStreamEnabledChanged(it)
                        }
                    )
                }
                
                Divider()
                
                // Vibration toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translated("Vibration on Stream"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = translated("Vibrate when new text arrives from streaming responses"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { 
                            vibrationEnabled = it
                            onVibrationEnabledChanged(it)
                        }
                    )
                }
                
                Divider()
                
                // Show thinking toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translated("Show Thinking"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = translated("Show thinking process from thinking models"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showThinking,
                        onCheckedChange = { 
                            onShowThinkingChanged(it)
                        }
                    )
                }
                
                Divider()

                // Model parameters (remote Ollama only)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = translated("Model Parameters"),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = {
                            temperatureText = ""
                            topPText = ""
                            topKText = ""
                            numCtxText = ""
                            seedText = ""
                        }) { Text(translated("Reset")) }
                    }
                    Text(
                        text = translated("Leave blank to use the model default. Applies to remote Ollama models only."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = temperatureText,
                        onValueChange = { temperatureText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(translated("Temperature")) },
                        placeholder = { Text("e.g. 0.8") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topPText,
                        onValueChange = { topPText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(translated("Top P")) },
                        placeholder = { Text("e.g. 0.9") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topKText,
                        onValueChange = { topKText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(translated("Top K")) },
                        placeholder = { Text("e.g. 40") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = numCtxText,
                        onValueChange = { numCtxText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(translated("Context length (num_ctx)")) },
                        placeholder = { Text("e.g. 4096") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(translated("Seed")) },
                        placeholder = { Text("e.g. 42") },
                        singleLine = true
                    )
                }

                Divider()

                // System prompt editor
                Column {
                    Text(
                        text = translated("System Prompt"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = translated("Modify how the AI responds by setting a system prompt"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onBrowseLibrary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(translated("Browse prompt library"))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(translated("Enter system prompt (optional)")) },
                        minLines = 4,
                        maxLines = 8,
                        singleLine = false
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSystemPromptChanged(systemPrompt)
                    onModelParamsChanged(
                        temperatureText.trim().toFloatOrNull(),
                        topPText.trim().toFloatOrNull(),
                        topKText.trim().toIntOrNull(),
                        numCtxText.trim().toIntOrNull(),
                        seedText.trim().toIntOrNull()
                    )
                    onDismiss()
                }
            ) {
                Text(translated("Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(translated("Cancel"))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorDialog(
    currentModel: String?,
    availableModels: List<com.charles.ollama.client.domain.model.Model>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(translated("Select Model"))
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                        contentDescription = translated("Refresh")
                    )
                }
            }
        },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (availableModels.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(translated("No models available"))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = translated("Make sure your server is connected and has models installed"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(availableModels, key = { it.name }) { model ->
                        val canSelect =
                            !model.isOnDeviceLitert() || model.isLitertDownloaded()
                        Card(
                            onClick = { if (canSelect) onSelect(model.name) },
                            enabled = canSelect,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentModel == model.name)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = model.displayTitle(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (currentModel == model.name) FontWeight.Bold else FontWeight.Normal
                                )
                                if (model.parameterSize != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = translated("Parameters: %s", model.parameterSize),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!canSelect) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = translated("Download this bundle on the Models screen first"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(translated("Close"))
            }
        }
    )
}

private sealed interface ChatRow {
    val key: String
    data class Msg(val message: ChatMessage) : ChatRow {
        override val key: String get() = "m-${message.id}"
    }
    data object Ad : ChatRow {
        override val key: String get() = "ad-native"
    }
}

private fun buildChatRows(messages: List<ChatMessage>, adAfter: Int): List<ChatRow> {
    if (messages.size <= adAfter) return messages.map(ChatRow::Msg)
    val out = ArrayList<ChatRow>(messages.size + 1)
    messages.forEachIndexed { i, m ->
        out.add(ChatRow.Msg(m))
        if (i == adAfter - 1) out.add(ChatRow.Ad)
    }
    return out
}
