package com.charles.ollama.client.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
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
    onNavigateToRewards: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ChatThreadsViewModel = hiltViewModel()
) {
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
    val showArchived by viewModel.showArchived.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var hasWaitedForServer by remember { mutableStateOf(false) }
    
    LaunchedEffect(hasServer) {
        if (hasServer) {
            hasWaitedForServer = true
        }
    }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        hasWaitedForServer = true
        if (!hasServer) {
            onNavigateToServers()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showArchived) "Archived" else "Chat Threads") },
                actions = {
                    IconButton(onClick = onNavigateToRewards) {
                        Icon(Icons.Default.CardGiftcard, contentDescription = "Rewards")
                    }
                    IconButton(onClick = onNavigateToModels) {
                        Icon(Icons.Default.Download, contentDescription = "Models")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Tune, contentDescription = "App settings")
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
        } else if (threads.isEmpty() && !isLoading && searchQuery.isBlank() && !showArchived) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                com.charles.ollama.client.ui.components.EmptyState(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "No chats yet",
                    subtitle = "Tap the + button to start your first conversation."
                )
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
                val nativeAdAfter = rememberSaveable { (1..3).random() }
                val rows = remember(threads, nativeAdAfter, showArchived) {
                    buildThreadRows(threads, nativeAdAfter, showArchived)
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(rows, key = ThreadRow::key) { row ->
                        when (row) {
                            is ThreadRow.SectionHeader -> SectionHeader(row.title)
                            is ThreadRow.Thread -> ThreadItem(
                                thread = row.thread,
                                onClick = { onNavigateToChat(row.thread.id) },
                                onTogglePinned = {
                                    viewModel.setPinned(row.thread.id, !row.thread.isPinned)
                                },
                                onToggleArchived = {
                                    viewModel.setArchived(row.thread.id, !row.thread.isArchived)
                                },
                                onShare = { viewModel.shareThread(row.thread.id) },
                                onDelete = { viewModel.deleteThread(row.thread.id) }
                            )
                            is ThreadRow.Ad -> NativeAdCard()
                        }
                    }
                }

                if (archivedCount > 0 || showArchived) {
                    HorizontalDivider()
                    TextButton(
                        onClick = { viewModel.toggleShowArchived() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (showArchived) "Show active threads"
                            else "Show archived ($archivedCount)"
                        )
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadItem(
    thread: ChatThread,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thread.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
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
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More actions"
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (thread.isPinned) "Unpin" else "Pin") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (thread.isPinned) Icons.Outlined.PushPin
                                else Icons.Filled.PushPin,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onTogglePinned()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (thread.isArchived) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (thread.isArchived) Icons.Filled.Unarchive
                                else Icons.Filled.Archive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleArchived()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            confirmDelete = true
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete thread?") },
            text = { Text("This will permanently delete \"${thread.title}\" and all its messages.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            }
        )
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
    data class SectionHeader(val title: String) : ThreadRow {
        override val key: String get() = "h-$title"
    }
    data class Thread(val thread: ChatThread) : ThreadRow {
        override val key: String get() = "t-${thread.id}"
    }
    data object Ad : ThreadRow {
        override val key: String get() = "ad-native"
    }
}

private fun buildThreadRows(
    threads: List<ChatThread>,
    adAfter: Int,
    showArchived: Boolean,
): List<ThreadRow> {
    if (threads.isEmpty()) return emptyList()
    val out = ArrayList<ThreadRow>(threads.size + 3)
    if (showArchived) {
        threads.forEachIndexed { i, t ->
            out.add(ThreadRow.Thread(t))
            if (i == adAfter - 1 && threads.size > adAfter) out.add(ThreadRow.Ad)
        }
        return out
    }
    val pinned = threads.filter { it.isPinned }
    val unpinned = threads.filter { !it.isPinned }
    if (pinned.isNotEmpty()) {
        out.add(ThreadRow.SectionHeader("Pinned"))
        pinned.forEach { out.add(ThreadRow.Thread(it)) }
        if (unpinned.isNotEmpty()) out.add(ThreadRow.SectionHeader("Threads"))
    }
    unpinned.forEachIndexed { i, t ->
        out.add(ThreadRow.Thread(t))
        if (i == adAfter - 1 && unpinned.size > adAfter) out.add(ThreadRow.Ad)
    }
    return out
}
