package com.charles.ollama.client.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Image
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
import com.charles.ollama.client.ui.models.displayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import android.util.Base64
import com.charles.ollama.client.util.ImageCompressionHelper
import com.charles.ollama.client.util.PerformanceMonitor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    threadId: Long,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    // Performance monitoring for screen rendering
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("ChatScreen") }
    LaunchedEffect(Unit) {
        PerformanceMonitor.addAttribute(screenTrace, "thread_id", threadId.toString())
    }
    DisposableEffect(Unit) {
        onDispose {
            PerformanceMonitor.stopTrace(screenTrace)
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
    
    var messageText by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<String>>(emptyList()) } // Base64 encoded images
    var showModelSelector by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showTitleEditDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
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
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(chatRows.lastIndex.coerceAtLeast(0))
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(thread?.title ?: "Chat")
                        selectedModel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showChatSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Chat Settings"
                        )
                    }
                    IconButton(onClick = { showModelSelector = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Select Model"
                        )
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
                                onLoadImages = { messageId -> viewModel.loadMessageImages(messageId) }
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
                                    contentDescription = "Remove image",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
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
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = selectedModel != null, // Allow typing even while loading
                    singleLine = false,
                    maxLines = 4
                )
                val canSend = (messageText.isNotBlank() || selectedImages.isNotEmpty()) && selectedModel != null
                FloatingActionButton(
                    onClick = {
                        if (canSend) {
                            val imagesToSend = if (selectedImages.isNotEmpty()) selectedImages else null
                            android.util.Log.d("ChatScreen", "Send button clicked: text='$messageText', images=${imagesToSend?.size ?: 0}")
                            viewModel.sendMessage(messageText, imagesToSend)
                            messageText = ""
                            selectedImages = emptyList()
                        } else {
                            android.util.Log.w("ChatScreen", "Send button clicked but canSend=false: text='$messageText', images=${selectedImages.size}, isLoading=$isLoading, model=$selectedModel")
                        }
                    },
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
                    Icon(Icons.Default.Send, contentDescription = "Send")
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
    onShowThinkingChanged: (Boolean) -> Unit
) {
    var streamEnabled by remember { mutableStateOf(thread?.streamEnabled ?: true) }
    var systemPrompt by remember { mutableStateOf(thread?.systemPrompt ?: "") }
    var vibrationEnabled by remember { mutableStateOf(thread?.vibrationEnabled ?: true) }
    
    LaunchedEffect(thread) {
        streamEnabled = thread?.streamEnabled ?: true
        systemPrompt = thread?.systemPrompt ?: ""
        vibrationEnabled = thread?.vibrationEnabled ?: true
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat Settings") },
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
                            text = "Stream Responses",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Enable streaming to see AI responses in real-time",
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
                            text = "Vibration on Stream",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Vibrate when new text arrives from streaming responses",
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
                            text = "Show Thinking",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Show thinking process from thinking models",
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
                
                // System prompt editor
                Column {
                    Text(
                        text = "System Prompt",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Modify how the AI responds by setting a system prompt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Enter system prompt (optional)") },
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
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
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
                Text("Select Model")
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                        contentDescription = "Refresh"
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
                    Text("No models available")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Make sure your server is connected and has models installed",
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
                                        text = "Parameters: ${model.parameterSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!canSelect) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Download this bundle on the Models screen first",
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
                Text("Close")
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

