package com.charles.ollama.client.ui.servers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.domain.model.Server
import com.charles.ollama.client.ui.components.ErrorDialog
import com.charles.ollama.client.ui.components.LoadingIndicator
import com.charles.ollama.client.ui.components.ServerCard
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.util.PerformanceMonitor
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServersViewModel = hiltViewModel()
) {
    // Performance monitoring for screen rendering
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("ServerListScreen") }
    DisposableEffect(Unit) {
        onDispose {
            PerformanceMonitor.stopTrace(screenTrace)
        }
    }
    
    val servers by viewModel.servers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<Server?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        },
        bottomBar = {
            BannerAd()
        }
    ) { padding ->
        if (isLoading && servers.isEmpty()) {
            LoadingIndicator()
        } else if (servers.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("No servers configured")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Add a server to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        onEdit = { editingServer = server },
                        onDelete = { viewModel.deleteServer(server) },
                        onSetDefault = { viewModel.setDefaultServer(server.id) }
                    )
                }
            }
        }
    }
    
    if (showAddDialog) {
        ServerDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave = { name, baseUrl, isDefault ->
                viewModel.addServer(name, baseUrl, isDefault)
                showAddDialog = false
            }
        )
    }
    
    editingServer?.let { server ->
        ServerDialog(
            viewModel = viewModel,
            server = server,
            onDismiss = { editingServer = null },
            onSave = { name, baseUrl, isDefault ->
                viewModel.updateServer(
                    server.copy(
                        name = name,
                        baseUrl = baseUrl,
                        isDefault = isDefault
                    )
                )
                editingServer = null
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
fun ServerDialog(
    viewModel: ServersViewModel,
    server: Server? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var baseUrl by remember { mutableStateOf(server?.baseUrl ?: "http://localhost:11434") }
    var isDefault by remember { mutableStateOf(server?.isDefault ?: false) }
    
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()
    
    AlertDialog(
        onDismissRequest = {
            viewModel.clearConnectionTestResult()
            onDismiss()
        },
        title = { Text(if (server == null) "Add Server" else "Edit Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    singleLine = true
                )
                // Native EditText for Firebase Robo Test compatibility
                AndroidView(
                    factory = { context ->
                        EditText(context).apply {
                            id = ViewCompat.generateViewId()
                            hint = "http://localhost:11434"
                            setSingleLine(true)
                            // Set accessibility properties for Firebase Robo Test
                            ViewCompat.setAccessibilityDelegate(this, object : androidx.core.view.AccessibilityDelegateCompat() {
                                override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfoCompat) {
                                    super.onInitializeAccessibilityNodeInfo(host, info)
                                    info.viewIdResourceName = "base_url"
                                }
                            })
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("baseURL"),
                    update = { view ->
                        if (view.text.toString() != baseUrl) {
                            view.setText(baseUrl)
                        }
                        view.setOnFocusChangeListener { _, hasFocus ->
                            if (!hasFocus) {
                                baseUrl = view.text.toString()
                            }
                        }
                        view.addTextChangedListener(object : android.text.TextWatcher {
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                baseUrl = s?.toString() ?: ""
                            }
                            override fun afterTextChanged(s: android.text.Editable?) {}
                        })
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isDefault,
                            onCheckedChange = { isDefault = it }
                        )
                        Text("Set as default")
                    }
                    Button(
                        onClick = { viewModel.testConnection(baseUrl) },
                        enabled = baseUrl.isNotBlank() && !isTestingConnection
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Test")
                        }
                    }
                }
                connectionTestResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.contains("successful", ignoreCase = true))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, baseUrl, isDefault) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.clearConnectionTestResult()
                onDismiss()
            }) {
                Text("Cancel")
            }
        }
    )
}

