package com.charles.ollama.client.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.charles.ollama.client.domain.model.ChatThread
import com.charles.ollama.client.ui.components.ErrorDialog
import com.charles.ollama.client.ui.components.LoadingIndicator
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.ui.components.NativeAdCard
import com.charles.ollama.client.util.PerformanceMonitor
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadsScreen(
    onNavigateToChat: (Long) -> Unit,
    onNavigateToServers: () -> Unit,
    onNavigateToModels: () -> Unit,
    viewModel: ChatThreadsViewModel = hiltViewModel()
) {
    // Performance monitoring for screen rendering
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("ChatThreadsScreen") }
    DisposableEffect(Unit) {
        onDispose {
            PerformanceMonitor.stopTrace(screenTrace)
        }
    }
    
    val scope = rememberCoroutineScope()
    val threads by viewModel.threads.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasServer by viewModel.hasServer.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var hasWaitedForServer by remember { mutableStateOf(false) }
    
    // Wait for hasServer to be determined before navigating
    // This prevents navigating to Servers when hasServer is just loading (initialValue=false)
    LaunchedEffect(hasServer) {
        // If hasServer becomes true, we're good - mark as waited
        if (hasServer) {
            hasWaitedForServer = true
        }
    }
    
    // Wait a bit to see if hasServer loads, then check
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300) // Give hasServer time to load from database
        hasWaitedForServer = true
        // After delay, check if we should navigate (only if hasServer is still false)
        if (!hasServer) {
            onNavigateToServers()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Threads") },
                actions = {
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Default.Download, contentDescription = "Models")
                    }
                    IconButton(onClick = onNavigateToServers) {
                        Icon(Icons.Default.Settings, contentDescription = "Servers")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Thread")
            }
        },
        bottomBar = {
            BannerAd()
        }
    ) { padding ->
        if (isLoading && threads.isEmpty()) {
            LoadingIndicator()
        } else if (threads.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No chat threads yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create a new thread to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    placeholder = { Text("Search threads...") },
                    singleLine = true
                )
                
                // Pick the slot where a native ad is interleaved among threads.
                // Stable across recompositions/process death so the ad doesn't
                // jump around mid-scroll. Skipped when the list is too short.
                val nativeAdAfter = rememberSaveable { (1..3).random() }
                val rows = remember(threads, nativeAdAfter) {
                    buildThreadRows(threads, nativeAdAfter)
                }
                LazyColumn {
                    items(rows, key = ThreadRow::key) { row ->
                        when (row) {
                            is ThreadRow.Thread -> ThreadItem(
                                thread = row.thread,
                                onClick = { onNavigateToChat(row.thread.id) },
                                onDelete = { viewModel.deleteThread(row.thread.id) }
                            )
                            is ThreadRow.Ad -> NativeAdCard()
                        }
                    }
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateThreadDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title ->
                scope.launch {
                    val threadId = viewModel.createThreadAsync(title, null)
                    if (threadId > 0) {
                        onNavigateToChat(threadId)
                    }
                }
                showCreateDialog = false
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
fun ThreadItem(
    thread: ChatThread,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = thread.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                thread.model?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(thread.updatedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete"
                )
            }
        }
    }
}

@Composable
fun CreateThreadDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Chat Thread") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Thread Title") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title) },
                enabled = title.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private sealed interface ThreadRow {
    val key: String
    data class Thread(val thread: ChatThread) : ThreadRow {
        override val key: String get() = "t-${thread.id}"
    }
    data object Ad : ThreadRow {
        override val key: String get() = "ad-native"
    }
}

private fun buildThreadRows(threads: List<ChatThread>, adAfter: Int): List<ThreadRow> {
    if (threads.size <= adAfter) return threads.map(ThreadRow::Thread)
    val out = ArrayList<ThreadRow>(threads.size + 1)
    threads.forEachIndexed { i, t ->
        out.add(ThreadRow.Thread(t))
        if (i == adAfter - 1) out.add(ThreadRow.Ad)
    }
    return out
}

