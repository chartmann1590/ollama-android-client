package com.charles.ollama.client.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Consistent corner-radius language. Material 3 maps these onto component
 * defaults (cards, buttons, sheets, etc.) so the whole app rounds uniformly
 * instead of the ad-hoc 8/20/24dp mix that existed before.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
