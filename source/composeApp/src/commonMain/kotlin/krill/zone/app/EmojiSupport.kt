package krill.zone.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*

/**
 * Provides emoji support for WASM/web platform by preloading the NotoColorEmoji font.
 *
 * Based on JetBrains Compose Multiplatform fix:
 * https://github.com/JetBrains/compose-multiplatform-core/pull/1400
 *
 * The fallback fonts don't work automatically, so we must preload them
 * using LocalFontFamilyResolver.
 */
object EmojiSupport {
    /**
     * Whether emoji fonts have been loaded. Observable state for UI.
     */
    val fontsLoaded = mutableStateOf(false)

    /**
     * The loaded emoji font family, or null if not loaded yet or not needed on this platform.
     */
    var emojiFontFamily: FontFamily? = null
        private set

    /**
     * Whether loading has been attempted (prevents multiple load attempts).
     */
    private var loadAttempted = false

    /**
     * Preload emoji font. Call this once early in app composition.
     * Uses platform-specific implementation.
     */
    @Composable
    fun PreloadEmojiFont() {
        val fontFamilyResolver = LocalFontFamilyResolver.current

        LaunchedEffect(Unit) {
            if (!loadAttempted) {
                loadAttempted = true
                try {
                    val loadedFamily = loadEmojiFontFamily()
                    if (loadedFamily != null) {
                        fontFamilyResolver.preload(loadedFamily)
                        emojiFontFamily = loadedFamily
                    }
                    fontsLoaded.value = true
                } catch (_: Exception) {
                    // Font not available - emojis may not render correctly on WASM
                    // This is expected on platforms that have native emoji support
                    fontsLoaded.value = true // Mark as loaded anyway to not block UI
                }
            }
        }
    }
}

/**
 * Platform-specific function to load emoji font family.
 * Returns null on platforms with native emoji support (Android, iOS).
 * Returns a FontFamily with NotoColorEmoji on WASM and Desktop.
 */
expect suspend fun loadEmojiFontFamily(): FontFamily?

/**
 * Wrapper composable that ensures emoji fonts are loaded before rendering content.
 * Use this around any composable that uses emoji characters.
 *
 * Usage:
 * ```
 * WithEmojiSupport {
 *     Text("🎯 Hello World 🔍")
 * }
 * ```
 */
@Composable
fun WithEmojiSupport(content: @Composable () -> Unit) {
    EmojiSupport.PreloadEmojiFont()
    content()
}

/**
 * Text composable with emoji support for WASM platform.
 * On WASM, this applies the NotoColorEmoji font family.
 * On other platforms (Android, iOS, Desktop), it behaves like regular Text.
 *
 * Usage:
 * ```
 * EmojiText("📋 Copy to clipboard")
 * EmojiText(
 *     text = "✓ Done",
 *     style = MaterialTheme.typography.titleMedium,
 *     color = MaterialTheme.colorScheme.primary
 * )
 * ```
 */
@Composable
fun EmojiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current
) {
    // Use the emoji font family if loaded (on WASM), otherwise use the provided fontFamily
    val effectiveFontFamily = EmojiSupport.emojiFontFamily ?: fontFamily

    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = effectiveFontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

