package com.charles.ollama.client.ui.update

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Overlay composable that fires a one-shot GitHub release check on first
 * composition and shows an [AlertDialog] if a newer release has been
 * published. The user can open the release in the browser (which is where
 * the signed APK lives) or dismiss until the next release.
 */
@Composable
fun UpdateAvailablePrompt(
    viewModel: UpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val update by viewModel.availableUpdate.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkOnce()
    }

    val info = update ?: return

    AlertDialog(
        onDismissRequest = { viewModel.clear() },
        title = { Text("Update available") },
        text = {
            val summary = info.bodyMarkdown.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?: "A newer build has been published on GitHub."
            Text("${info.name}\n\n$summary")
        },
        confirmButton = {
            TextButton(onClick = {
                val target = info.apkAssetUrl ?: info.releaseUrl
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                viewModel.clear()
            }) {
                Text(if (info.apkAssetUrl != null) "Download APK" else "Open release")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismiss() }) {
                Text("Not now")
            }
        }
    )
}
