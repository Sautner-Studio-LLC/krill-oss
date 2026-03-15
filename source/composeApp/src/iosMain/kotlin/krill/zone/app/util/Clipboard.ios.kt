package krill.zone.app.util

import androidx.compose.runtime.*
import platform.UIKit.*

actual fun copyToClipboard(text: String) {
    UIPasteboard.generalPasteboard.string = text
}

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit {
    return remember { { text: String -> copyToClipboard(text) } }
}

