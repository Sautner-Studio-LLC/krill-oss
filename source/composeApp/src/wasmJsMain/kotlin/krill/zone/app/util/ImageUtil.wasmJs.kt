package krill.zone.app.util

import androidx.compose.ui.graphics.*
import org.jetbrains.skia.Image as SkiaImage

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? {
    return try {
        SkiaImage.makeFromEncoded(this).toComposeImageBitmap()
    } catch (_: Exception) { null }
}
