package krill.zone.app

import androidx.compose.ui.text.font.*

/**
 * Android implementation - Android has native emoji support via system fonts.
 * Returns null to skip font loading.
 */
actual suspend fun loadEmojiFontFamily(): FontFamily? {
    // Android has native emoji support via system fonts
    return null
}

