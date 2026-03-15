package krill.zone.app

import androidx.compose.ui.text.font.*

/**
 * Desktop (JVM) implementation - desktops typically have native emoji support.
 * Returns null to skip font loading.
 */
actual suspend fun loadEmojiFontFamily(): FontFamily? {
    // Desktop platforms typically have native emoji support via system fonts
    return null
}

