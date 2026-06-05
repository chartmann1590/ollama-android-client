package com.charles.ollama.client.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single spacing scale so screens stop hand-picking 8/12/16/24dp ad-hoc.
 * Access via [LocalSpacing] inside composables, e.g.
 * `val spacing = LocalSpacing.current; Modifier.padding(spacing.md)`.
 */
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
