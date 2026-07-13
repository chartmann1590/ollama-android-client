package com.charles.ollama.client.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.charles.ollama.client.ui.localization.translated

@Composable
fun ErrorDialog(
    title: String = "Error",
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = translated(title)) },
        text = { Text(text = translated(message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(translated("OK"))
            }
        },
        modifier = modifier
    )
}

