package com.charles.ollama.client.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Base64
import com.charles.ollama.client.domain.model.ChatMessage

@Composable
fun MessageBubble(
    message: ChatMessage,
    showThinking: Boolean = false,
    modifier: Modifier = Modifier,
    onLoadImages: ((Long) -> Unit)? = null
) {
    val isUser = message.role == "user"
    val hasThinking = message.thinking != null && message.thinking.isNotBlank()
    var isThinkingExpanded by remember { mutableStateOf(showThinking && hasThinking) }
    
    // Try to load images on-demand if they're missing for a user message
    LaunchedEffect(message.id, message.images) {
        if (isUser && message.images == null && message.id > 0 && onLoadImages != null) {
            // Try loading images on-demand for user messages that should have them
            onLoadImages(message.id)
        }
    }
    
    // Debug logging
    LaunchedEffect(message.id, hasThinking, showThinking) {
        if (!isUser) {
            android.util.Log.d("MessageBubble", "Message ${message.id}: hasThinking=$hasThinking, showThinking=$showThinking, thinking=${message.thinking?.take(50)}")
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
            // Show thinking section if thinking exists OR if showThinking is enabled
            val shouldShowThinkingSection = !isUser && (hasThinking || showThinking)
            if (shouldShowThinkingSection) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            // Show a hint that thinking is enabled but no content found
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
                modifier = Modifier.fillMaxWidth(),
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
                    
                    // Display text content if present
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
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
        
        if (isUser) {
            Spacer(modifier = Modifier.width(48.dp))
        }
    }
}

