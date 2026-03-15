package krill.zone.app

import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.platform.*
import krill.composeapp.generated.resources.*
import org.jetbrains.compose.resources.*

/**
 * WASM/JS implementation - loads NotoColorEmoji font from resources.
 *
 * To enable emoji support on WASM:
 * 1. Download NotoColorEmoji.ttf from Google Fonts
 * 2. Place it in composeApp/src/commonMain/composeResources/font/
 * 3. The font will be auto-generated as Res.font.NotoColorEmoji
 *
 * For now, returns null since WASM has limited emoji support without custom fonts.
 * The emoji characters will display as boxes or question marks on unsupported browsers.
 *
 * When the font is added, update this to:
 * ```kotlin
 * actual suspend fun loadEmojiFontFamily(): FontFamily? {
 *     return FontFamily(Font(Res.font.NotoColorEmoji))
 * }
 * ```
 */
@OptIn(ExperimentalResourceApi::class)
actual suspend fun loadEmojiFontFamily(): FontFamily? {
    val bytes = Res.readBytes("font/NotoColorEmoji-Regular.ttf")
    return FontFamily(Font("NotoColorEmoji", bytes))
}

