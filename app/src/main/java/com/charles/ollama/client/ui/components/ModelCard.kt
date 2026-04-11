package com.charles.ollama.client.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.charles.ollama.client.domain.model.Model
import com.charles.ollama.client.ui.models.displayTitle

@Composable
fun ModelCard(
    model: Model,
    onDelete: () -> Unit,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayTitle(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(model.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    model.parameterSize?.let {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Parameters: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (model.isOnDeviceLitert() && !model.isLitertDownloaded()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Not downloaded — tap Download to add this bundle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (model.isOnDeviceLitert() && !model.isLitertDownloaded() && onDownload != null) {
                        FilledTonalButton(onClick = onDownload) {
                            Text("Download")
                        }
                    }
                    if (!model.isOnDeviceLitert() || model.isLitertDownloaded()) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete model"
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> String.format("%.2f GB", gb)
        mb >= 1 -> String.format("%.2f MB", mb)
        kb >= 1 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}

