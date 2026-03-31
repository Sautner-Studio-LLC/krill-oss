package krill.zone.app.util

import androidx.compose.ui.graphics.*

/**
 * Decodes a JPEG/PNG byte array into a Compose ImageBitmap.
 * Platform-specific: Skia on Desktop/iOS/WASM, BitmapFactory on Android.
 */
expect fun ByteArray.decodeToImageBitmap(): ImageBitmap?
