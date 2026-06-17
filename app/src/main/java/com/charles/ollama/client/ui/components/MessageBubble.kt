package com.charles.ollama.client.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.ollama.client.R
import com.charles.ollama.client.domain.model.ChatMessage

private fun copyMessageToClipboard(
    clipboardManager: ClipboardManager,
    context: Context,
    text: String,
    successMessage: String,
    emptyMessage: String
) {
    val trimmedText = text.trim()
    if (trimmedText.isBlank()) {
        Toast.makeText(context, emptyMessage, Toast.LENGTH_SHORT).show()
        return
    }

    clipboardManager.setText(AnnotatedString(trimmedText))
    Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
}

/**
 * Formats Ollama generation metrics into a compact caption like
 * "38 tok/s · 312 tokens · 4.1s". Returns null when no usable metrics are present
 * (e.g. on-device LiteRT replies or older messages).
 */
private fun formatGenerationStats(message: ChatMessage): String? {
    val evalCount = message.evalCount ?: return null
    if (evalCount <= 0) return null
    val parts = mutableListOf<String>()
    val evalNs = message.evalDurationNs
    if (evalNs != null && evalNs > 0) {
        val toksPerSec = evalCount * 1_000_000_000.0 / evalNs
        parts.add(String.format("%.0f tok/s", toksPerSec))
    }
    parts.add("$evalCount tokens")
    val totalNs = message.totalDurationNs
    if (totalNs != null && totalNs > 0) {
        parts.add(String.format("%.1fs", totalNs / 1_000_000_000.0))
    }
    return parts.joinToString(" · ")
}

/**
 * Highlights [query] inside [text] with a bold span. Used by in-thread search.
 * Matches are case-insensitive; if [query] is blank, returns [text] unchanged.
 */
private fun highlightMatches(text: String, query: String?): AnnotatedString {
    val q = query?.trim().orEmpty()
    if (q.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val lowerText = text.lowercase()
        val lowerQuery = q.lowercase()
        var idx = lowerText.indexOf(lowerQuery)
        while (idx >= 0) {
            addStyle(
                style = SpanStyle(fontWeight = FontWeight.ExtraBold),
                start = idx,
                end = idx + lowerQuery.length
            )
            idx = lowerText.indexOf(lowerQuery, idx + lowerQuery.length)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MessageBubble(
    message: ChatMessage,
    showThinking: Boolean = false,
    modifier: Modifier = Modifier,
    onLoadImages: ((Long) -> Unit)? = null,
    highlightQuery: String? = null,
    onShare: ((Long) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    onReadAloud: ((String) -> Unit)? = null,
    onRegenerate: ((Long) -> Unit)? = null,
    onEditAndResend: ((Long, String) -> Unit)? = null,
    onReport: ((Long) -> Unit)? = null,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copySuccessMessage = stringResource(id = R.string.message_copy_success)
    val nothingToCopyMessage = stringResource(id = R.string.message_copy_nothing)
    val isUser = message.role == "user"
    val hasThinking = message.thinking != null && message.thinking.isNotBlank()
    var isThinkingExpanded by remember { mutableStateOf(showThinking && hasThinking) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReportConfirm by remember { mutableStateOf(false) }
    
    // Try to load images on-demand if they're missing for a user message
    LaunchedEffect(message.id, message.images) {
        if (isUser && message.images == null && message.id > 0 && onLoadImages != null) {
            onLoadImages(message.id)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Column(
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // Thinking section (only for assistant messages)
            val shouldShowThinkingSection = !isUser && (hasThinking || showThinking)
            if (shouldShowThinkingSection) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                copyMessageToClipboard(
                                    clipboardManager = clipboardManager,
                                    context = context,
                                    text = message.thinking.orEmpty(),
                                    successMessage = copySuccessMessage,
                                    emptyMessage = nothingToCopyMessage
                                )
                            }
                        ),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isThinkingExpanded) 4.dp else 16.dp,
                        bottomEnd = if (isThinkingExpanded) 4.dp else 16.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thinking",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            IconButton(
                                onClick = { isThinkingExpanded = !isThinkingExpanded },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isThinkingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isThinkingExpanded) "Hide thinking" else "Show thinking",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        
                        if (isThinkingExpanded) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                            Text(
                                text = message.thinking ?: "No thinking content available",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        } else if (!hasThinking && showThinking) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                            )
                            Text(
                                text = "Thinking enabled but no thinking content detected in this response.\n\nThe model may not be outputting thinking tags, or thinking may be disabled in the model configuration.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
            
            // Main message content
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            copyMessageToClipboard(
                                clipboardManager = clipboardManager,
                                context = context,
                                text = message.content,
                                successMessage = copySuccessMessage,
                                emptyMessage = nothingToCopyMessage
                            )
                        }
                    ),
                shape = RoundedCornerShape(
                    topStart = if (shouldShowThinkingSection && isThinkingExpanded) 4.dp else 16.dp,
                    topEnd = if (shouldShowThinkingSection && isThinkingExpanded) 4.dp else 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Display images if present
                    message.images?.let { images ->
                        if (images.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                images.forEach { base64Image ->
                                    val bitmap = remember(base64Image) {
                                        try {
                                            val imageBytes = Base64.decode(base64Image, Base64.NO_WRAP)
                                            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    bitmap?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = "Image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 300.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                            }
                            if (message.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                    
                    // Display text content if present, wrapped so users can drag-select
                    // and use the system "Share" / "Translate" actions on a snippet.
                    if (message.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = highlightMatches(message.content, highlightQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Generation stats caption (assistant replies from remote Ollama; the
            // on-device LiteRT backend reports no counters so this stays hidden).
            if (!isUser) {
                val stats = remember(message.evalCount, message.evalDurationNs, message.totalDurationNs) {
                    formatGenerationStats(message)
                }
                if (stats != null) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                    )
                }
            }

            // Per-message actions row. Only render the overflow if the host
            // wired up at least one action callback and the message has been
            // persisted (id > 0 so id-based actions like delete/regen work).
            val hasActions = (onShare != null || onDelete != null || onReadAloud != null ||
                onRegenerate != null || onEditAndResend != null ||
                (onReport != null && !isUser)) && message.id > 0
            if (hasActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Message actions",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy") },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    copyMessageToClipboard(
                                        clipboardManager = clipboardManager,
                                        context = context,
                                        text = message.content,
                                        successMessage = copySuccessMessage,
                                        emptyMessage = nothingToCopyMessage,
                                    )
                                },
                            )
                            if (onShare != null) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onShare(message.id)
                                    },
                                )
                            }
                            if (!isUser && onReadAloud != null) {
                                DropdownMenuItem(
                                    text = { Text("Read aloud") },
                                    leadingIcon = { Icon(Icons.Default.VolumeUp, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onReadAloud(message.content)
                                    },
                                )
                            }
                            if (!isUser && onRegenerate != null) {
                                DropdownMenuItem(
                                    text = { Text("Regenerate") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onRegenerate(message.id)
                                    },
                                )
                            }
                            if (isUser && onEditAndResend != null) {
                                DropdownMenuItem(
                                    text = { Text("Edit and resend") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showEditDialog = true
                                    },
                                )
                            }
                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showDeleteConfirm = true
                                    },
                                )
                            }
                            if (!isUser && onReport != null) {
                                DropdownMenuItem(
                                    text = { Text("Report") },
                                    leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showReportConfirm = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (isUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }

    if (showEditDialog && onEditAndResend != null) {
        EditMessageDialog(
            initial = message.content,
            onDismiss = { showEditDialog = false },
            onConfirm = { updated ->
                showEditDialog = false
                onEditAndResend(message.id, updated)
            },
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this message?") },
            text = {
                Text(
                    text = if (isUser)
                        "Deleting this message will also remove every reply that came after it."
                    else
                        "This will remove just this assistant reply."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete(message.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showReportConfirm && onReport != null) {
        AlertDialog(
            onDismissRequest = { showReportConfirm = false },
            title = { Text("Report this response?") },
            text = {
                Text(
                    "Flag this AI-generated reply as offensive, harmful, or otherwise " +
                        "inappropriate. Our team will review reported content."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReportConfirm = false
                    onReport(message.id)
                    Toast.makeText(
                        context,
                        "Thanks — this response has been reported for review.",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Report") }
            },
            dismissButton = {
                TextButton(onClick = { showReportConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EditMessageDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit and resend") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank() && text != initial,
            ) { Text("Resend") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
