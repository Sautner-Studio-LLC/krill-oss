package krill.zone.app.util

import android.graphics.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap

actual fun ByteArray.decodeToImageBitmap(): ImageBitmap? {
    return try {
        val bitmap = BitmapFactory.decodeByteArray(this, 0, this.size)
        bitmap?.asImageBitmap()
    } catch (_: Exception) { null }
}
