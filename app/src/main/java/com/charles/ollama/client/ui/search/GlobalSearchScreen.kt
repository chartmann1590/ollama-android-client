package com.charles.ollama.client.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.charles.ollama.client.data.database.dao.MessageSearchResult
import com.charles.ollama.client.ui.components.BannerAd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onNavigateBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::updateQuery,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search all chats…") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { BannerAd() }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                query.isBlank() -> CenteredHint("Type to search across all your conversations.")
                results.isEmpty() -> CenteredHint("No messages match \"$query\".")
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(results, key = { it.id }) { result ->
                        SearchResultItem(
                            result = result,
                            query = query,
                            onClick = { onOpenThread(result.threadId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchResultItem(
    result: MessageSearchResult,
    query: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.threadTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = highlight(result.snippet, query),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Bolds case-insensitive matches of [query] within [text]. */
private fun highlight(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val lower = text.lowercase()
        val lowerQ = q.lowercase()
        var idx = lower.indexOf(lowerQ)
        while (idx >= 0) {
            addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), idx, idx + lowerQ.length)
            idx = lower.indexOf(lowerQ, idx + lowerQ.length)
        }
    }
}
