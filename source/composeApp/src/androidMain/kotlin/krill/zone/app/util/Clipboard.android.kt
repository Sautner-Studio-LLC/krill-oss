package krill.zone.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual fun copyToClipboard(text: String) {
    // This function shouldn't be called directly on Android
    // Use rememberClipboardCopier instead which has access to Context
    throw UnsupportedOperationException("Use rememberClipboardCopier on Android")
}

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text: String ->
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", text)
            clipboardManager.setPrimaryClip(clip)
        }
    }
}

