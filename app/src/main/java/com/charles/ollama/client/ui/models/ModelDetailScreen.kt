package com.charles.ollama.client.ui.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.data.api.dto.ShowModelResponse
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.ui.components.ErrorDialog
import com.charles.ollama.client.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelDetailViewModel = hiltViewModel()
) {
    val info by viewModel.info.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.modelName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { BannerAd() }
    ) { padding ->
        when {
            isLoading && info == null -> LoadingIndicator()
            info == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No model info available")
                }
            }
            else -> {
                val detail = info!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DetailsCard(detail)
                    detail.parameters.takeIf { it.isNotBlank() }?.let {
                        CodeSection(title = "Parameters", body = it)
                    }
                    detail.template.takeIf { it.isNotBlank() }?.let {
                        CodeSection(title = "Template", body = it)
                    }
                    detail.system?.takeIf { it.isNotBlank() }?.let {
                        CodeSection(title = "System prompt", body = it)
                    }
                    detail.license?.takeIf { it.isNotBlank() }?.let {
                        CodeSection(title = "License", body = it, collapsible = true)
                    }
                    detail.modelfile.takeIf { it.isNotBlank() }?.let {
                        CodeSection(title = "Modelfile", body = it, collapsible = true)
                    }
                }
            }
        }
    }

    error?.let {
        ErrorDialog(message = it, onDismiss = viewModel::clearError)
    }
}

@Composable
private fun DetailsCard(detail: ShowModelResponse) {
    val d = detail.details
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            DetailRow("Family", d.family)
            DetailRow("Families", d.families?.joinToString(", "))
            DetailRow("Parameter size", d.parameterSize)
            DetailRow("Quantization", d.quantizationLevel)
            DetailRow("Format", d.format)
            DetailRow("Parent model", d.parentModel)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CodeSection(
    title: String,
    body: String,
    collapsible: Boolean = false
) {
    var expanded by remember { mutableStateOf(!collapsible) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (collapsible) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide" else "Show")
                    }
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = body.trim(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
