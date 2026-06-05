package com.charles.ollama.client.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.domain.prompt.PromptPreset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptLibraryScreen(
    onNavigateBack: () -> Unit,
    viewModel: PromptLibraryViewModel = hiltViewModel()
) {
    val custom by viewModel.customPresets.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val canApply = viewModel.threadId > 0L

    var editing by remember { mutableStateOf<PromptPreset?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(toast) {
        toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Prompt Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showEditor = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New preset") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
        ) {
            item {
                Text(
                    text = if (canApply) {
                        "Tap a persona to apply it to this chat, or save your own."
                    } else {
                        "Browse and manage personas. Open a chat to apply one."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Text("Built-in", style = MaterialTheme.typography.titleMedium)
            }
            items(viewModel.builtIns, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    canApply = canApply,
                    onApply = { viewModel.applyToThread(preset) { onNavigateBack() } },
                    onEdit = null,
                    onDelete = null
                )
            }

            item {
                Text("Your presets", style = MaterialTheme.typography.titleMedium)
            }
            if (custom.isEmpty()) {
                item {
                    Text(
                        text = "No saved presets yet — tap \"New preset\" to create one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(custom, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    canApply = canApply,
                    onApply = { viewModel.applyToThread(preset) { onNavigateBack() } },
                    onEdit = { editing = preset; showEditor = true },
                    onDelete = { viewModel.deleteCustom(preset.id) }
                )
            }
        }
    }

    if (showEditor) {
        PresetEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { title, text ->
                viewModel.saveCustom(title, text, editing?.id)
                showEditor = false
            }
        )
    }
}

@Composable
private fun PresetCard(
    preset: PromptPreset,
    canApply: Boolean,
    onApply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            Text(
                text = preset.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            if (canApply) {
                FilledTonalButton(
                    onClick = onApply,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Apply to chat")
                }
            }
        }
    }
}

@Composable
private fun PresetEditorDialog(
    initial: PromptPreset?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var text by remember { mutableStateOf(initial?.text ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New preset" else "Edit preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("System prompt") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, text) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
