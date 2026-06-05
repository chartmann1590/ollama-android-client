package com.charles.ollama.client.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette for the app's custom design language.
 *
 * The identity is "bold & techy": a deep near-black blue canvas in dark mode,
 * a clean off-white canvas in light mode, and an electric indigo -> teal accent
 * pairing that reads well in marketplace screenshots. These tokens are wired
 * into the Material 3 color schemes in Theme.kt.
 */

// ---- Core brand accents (used for gradients / highlights) ----
val BrandIndigo = Color(0xFF5B6CFF)
val BrandIndigoBright = Color(0xFF8B9CFF)
val BrandTeal = Color(0xFF1FD8C4)
val BrandTealBright = Color(0xFF4FE3D4)
val BrandViolet = Color(0xFFB16CFF)

/** Gradient endpoints for hero headers and the primary CTA button. */
val BrandGradientStart = Color(0xFF5B6CFF)
val BrandGradientEnd = Color(0xFF1FD8C4)

// =====================================================================
//  DARK SCHEME
// =====================================================================
val DarkPrimary = Color(0xFF8B9CFF)
val DarkOnPrimary = Color(0xFF0A0E2A)
val DarkPrimaryContainer = Color(0xFF2A327A)
val DarkOnPrimaryContainer = Color(0xFFDEE2FF)

val DarkSecondary = Color(0xFF4FE3D4)
val DarkOnSecondary = Color(0xFF00382F)
val DarkSecondaryContainer = Color(0xFF005046)
val DarkOnSecondaryContainer = Color(0xFF6FFFEE)

val DarkTertiary = Color(0xFFC99CFF)
val DarkOnTertiary = Color(0xFF2A0E4F)
val DarkTertiaryContainer = Color(0xFF43286B)
val DarkOnTertiaryContainer = Color(0xFFECDCFF)

val DarkBackground = Color(0xFF0B0D14)
val DarkOnBackground = Color(0xFFE4E6EE)
val DarkSurface = Color(0xFF0B0D14)
val DarkOnSurface = Color(0xFFE4E6EE)
val DarkSurfaceVariant = Color(0xFF2A2D3A)
val DarkOnSurfaceVariant = Color(0xFFC3C6D4)

val DarkSurfaceContainerLowest = Color(0xFF06070C)
val DarkSurfaceContainerLow = Color(0xFF12141C)
val DarkSurfaceContainer = Color(0xFF161823)
val DarkSurfaceContainerHigh = Color(0xFF1F2230)
val DarkSurfaceContainerHighest = Color(0xFF292C3B)

val DarkOutline = Color(0xFF8C90A0)
val DarkOutlineVariant = Color(0xFF43475A)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

// =====================================================================
//  LIGHT SCHEME
// =====================================================================
val LightPrimary = Color(0xFF4A5BD8)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDEE2FF)
val LightOnPrimaryContainer = Color(0xFF00115A)

val LightSecondary = Color(0xFF00A99A)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFF6FFFEE)
val LightOnSecondaryContainer = Color(0xFF00201C)

val LightTertiary = Color(0xFF8A4FD8)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFECDCFF)
val LightOnTertiaryContainer = Color(0xFF2C0051)

val LightBackground = Color(0xFFFBFAFF)
val LightOnBackground = Color(0xFF1A1B22)
val LightSurface = Color(0xFFFBFAFF)
val LightOnSurface = Color(0xFF1A1B22)
val LightSurfaceVariant = Color(0xFFE3E1EC)
val LightOnSurfaceVariant = Color(0xFF46474F)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF4F3FB)
val LightSurfaceContainer = Color(0xFFEFEDF6)
val LightSurfaceContainerHigh = Color(0xFFE9E7F0)
val LightSurfaceContainerHighest = Color(0xFFE3E1EA)

val LightOutline = Color(0xFF767680)
val LightOutlineVariant = Color(0xFFC7C5D0)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
