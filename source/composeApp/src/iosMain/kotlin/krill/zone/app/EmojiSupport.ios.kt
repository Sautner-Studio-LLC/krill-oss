package krill.zone.app

import androidx.compose.ui.text.font.*

/**
 * iOS implementation - iOS has native emoji support via system fonts.
 * Returns null to skip font loading.
 */
actual suspend fun loadEmojiFontFamily(): FontFamily? {
    // iOS has native emoji support via system fonts
    return null
}

