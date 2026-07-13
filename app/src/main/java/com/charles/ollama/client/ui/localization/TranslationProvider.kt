package com.charles.ollama.client.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.charles.ollama.client.data.translation.TranslationRepository

val LocalTranslationRepository = compositionLocalOf<TranslationRepository> {
    error("TranslationRepository was not provided")
}

@Composable
fun TranslationProvider(
    repository: TranslationRepository,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTranslationRepository provides repository) {
        content()
    }
}

@Composable
fun translated(source: String): String {
    val repository = LocalTranslationRepository.current
    val languageTag by repository.languageTag.collectAsState()
    val cacheVersion by repository.cacheVersion.collectAsState()
    var value by remember(source, languageTag, cacheVersion) {
        mutableStateOf(repository.cachedOrSource(source))
    }

    LaunchedEffect(source, languageTag) {
        value = repository.translate(source)
    }

    return value
}

@Composable
fun translated(source: String, vararg args: Any?): String {
    val formatted = remember(source, args.contentHashCode()) {
        source.format(*args)
    }
    return translated(formatted)
}
