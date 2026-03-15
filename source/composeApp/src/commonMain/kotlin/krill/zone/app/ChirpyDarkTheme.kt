package krill.zone.app

import androidx.compose.material3.*
import androidx.compose.ui.graphics.*

val ChirpyDarkColorScheme = darkColorScheme(
    // Chirpy link color -> primary
    primary = Color(0xFF8AB4F8),              // --link-color
    onPrimary = Color(0xFF0B1320),            // readable on light-blue primary
    primaryContainer = Color(0xFF526C96),     // --link-underline-color
    onPrimaryContainer = Color(0xFFE6F0FF),

    // Muted greys for secondary accents
    secondary = Color(0xFF868686),            // --text-muted-color
    onSecondary = Color(0xFF101010),
    secondaryContainer = Color(0xFF2E2F31),   // --btn-border-color
    onSecondaryContainer = Color(0xFFE0E0E0),

    // Labels/headings as tertiary
    tertiary = Color(0xFFA7A7A7),             // --label-color
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF292929),    // --card-header-bg
    onTertiaryContainer = Color(0xFFE0E0E0),

    // App surfaces/backdrops
    background = Color(0xFF1B1B1E),           // --main-bg
    onBackground = Color(0xFFAFB0B1),         // --text-color

    surface = Color(0xFF1E1E1E),              // --sidebar-bg / --button-bg
    onSurface = Color(0xFFAFB0B1),            // --text-color

    surfaceVariant = Color(0xFF2C2D2D),       // --main-border-color
    onSurfaceVariant = Color(0xFFA7A7A7),     // --label-color

    outline = Color(0xFF2C2D2D),              // --main-border-color
    outlineVariant = Color(0xFF353535),       // --code-header-muted-color

    // Danger/alert mapping
    error = Color(0xFFCD0202),                // --prompt-danger-icon-color
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF561C08),       // --prompt-danger-bg
    onErrorContainer = Color(0xFFD8D4D4),     // --prompt-text-color (approx)

    // Misc
    inverseSurface = Color(0xFFE6E6E6),       // near white (sidebar active)
    inverseOnSurface = Color(0xFF1B1B1E),     // --main-bg
    inversePrimary = Color(0xFF8AB4F8),       // reuse primary for tint
    surfaceTint = Color(0xFF8AB4F8),          // primary tint
    scrim = Color(0xFF444546)                 // --mask-bg
)
