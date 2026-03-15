package krill.zone.app.util

import androidx.compose.runtime.*
import java.awt.*
import java.awt.datatransfer.*

actual fun copyToClipboard(text: String) {
    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(text), null)
}

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit {
    return remember { { text: String -> copyToClipboard(text) } }
}

