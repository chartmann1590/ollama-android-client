package com.charles.ollama.client.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.data.litert.LocalModelCatalog
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.ui.components.ErrorDialog
import com.charles.ollama.client.ui.components.LoadingIndicator
import com.charles.ollama.client.ui.components.ModelCard
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.util.PerformanceMonitor
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelsViewModel = hiltViewModel()
) {
    // Performance monitoring for screen rendering
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("ModelsScreen") }
    DisposableEffect(Unit) {
        onDispose {
            PerformanceMonitor.stopTrace(screenTrace)
        }
    }
    
    val models by viewModel.models.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pullProgress by viewModel.pullProgress.collectAsState()
    val isPulling by viewModel.isPulling.collectAsState()
    val isLitertBackend by viewModel.isLitertBackend.collectAsState()
    
    var showPullDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadModels()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showPullDialog = true }) {
                        Text(if (isLitertBackend) "Download model" else "Pull Model")
                    }
                }
            )
        },
        bottomBar = {
            BannerAd()
        }
    ) { padding ->
        if (isLoading && models.isEmpty()) {
            LoadingIndicator()
        } else if (models.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No models available")
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                if (isPulling && pullProgress != null) {
                    val progress = pullProgress!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(if (isLitertBackend) "Downloading model…" else "Pulling model…")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress.progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatProgressLine(progress.status, progress.completed, progress.total),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                LazyColumn {
                    items(models, key = { it.name }) { model ->
                        ModelCard(
                            model = model,
                            onDelete = { viewModel.deleteModel(model.name) },
                            onDownload = if (isLitertBackend) {
                                { viewModel.pullModel(model.name) }
                            } else null
                        )
                    }
                }
            }
        }
    }
    
    if (showPullDialog) {
        PullModelDialog(
            litertMode = isLitertBackend,
            onDismiss = { showPullDialog = false },
            onPull = { modelName ->
                viewModel.pullModel(modelName)
                showPullDialog = false
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
fun PullModelDialog(
    onDismiss: () -> Unit,
    onPull: (String) -> Unit,
    litertMode: Boolean = false
) {
    var modelName by remember { mutableStateOf("") }
    var selectedCatalogId by remember {
        mutableStateOf(LocalModelCatalog.entries.firstOrNull()?.id ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (litertMode) "Download Gemma 4 (LiteRT)" else "Pull Model")
        },
        text = {
            if (litertMode) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Choose a Gemma 4 bundle. Large downloads use Wi‑Fi when possible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LocalModelCatalog.entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCatalogId == entry.id,
                                onClick = { selectedCatalogId = entry.id }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = formatApproxBytes(entry.approximateSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Model Name") },
                    placeholder = { Text("e.g., llama3.2") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (litertMode) {
                        onPull("litert/$selectedCatalogId")
                    } else {
                        onPull(modelName)
                    }
                },
                enabled = if (litertMode) selectedCatalogId.isNotBlank() else modelName.isNotBlank()
            ) {
                Text(if (litertMode) "Download" else "Pull")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatProgressLine(status: String, completed: Long, total: Long): String {
    if (total <= 1L) return status
    val pct = (completed.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0)
    return "$status — ${formatBytes(completed)} / ${formatBytes(total)} (%.0f%%)".format(pct)
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.0f MB".format(mb)
        else -> "${bytes / 1024} KB"
    }
}

private fun formatApproxBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        gb >= 1 -> String.format("~%.1f GB", gb)
        mb >= 1 -> String.format("~%.0f MB", mb)
        else -> "~${bytes / 1024} KB"
    }
}

