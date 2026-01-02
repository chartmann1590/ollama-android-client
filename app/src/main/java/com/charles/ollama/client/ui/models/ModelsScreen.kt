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
                        Text("Pull Model")
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Pulling model...")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(progress = pullProgress!!.progress)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pullProgress!!.status,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                LazyColumn {
                    items(models, key = { it.name }) { model ->
                        ModelCard(
                            model = model,
                            onDelete = { viewModel.deleteModel(model.name) }
                        )
                    }
                }
            }
        }
    }
    
    if (showPullDialog) {
        PullModelDialog(
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
    onPull: (String) -> Unit
) {
    var modelName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pull Model") },
        text = {
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                placeholder = { Text("e.g., llama3.2") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onPull(modelName) },
                enabled = modelName.isNotBlank()
            ) {
                Text("Pull")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

