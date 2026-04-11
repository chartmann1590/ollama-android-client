package com.charles.ollama.client.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.ui.components.BannerAd
import com.charles.ollama.client.util.PerformanceMonitor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val screenTrace = remember { PerformanceMonitor.startScreenTrace("SettingsScreen") }
    DisposableEffect(Unit) {
        onDispose { PerformanceMonitor.stopTrace(screenTrace) }
    }

    val huggingFaceToken by viewModel.huggingFaceToken.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { BannerAd() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "On-device LiteRT (Gemma)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Gemma 4 bundles from litert-community are public and don't need a token. This field is only required for gated Hugging Face repos — paste a read-only token from huggingface.co/settings/tokens if you hit a 401/403 on download.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = huggingFaceToken,
                onValueChange = viewModel::updateHuggingFaceToken,
                label = { Text("Hugging Face token") },
                placeholder = { Text("hf_…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    if (huggingFaceToken.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearHuggingFaceToken) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear token")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (huggingFaceToken.isBlank()) {
                    "No token set — gated models will fail to download."
                } else {
                    "Token saved on this device only."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
