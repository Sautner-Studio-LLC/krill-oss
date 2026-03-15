package krill.zone.app.util

import androidx.compose.runtime.*
import kotlinx.browser.*

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual fun copyToClipboard(text: String) {
    window.navigator.clipboard.writeText(text)
}

@Composable
actual fun rememberClipboardCopier(): (String) -> Unit {
    return remember { { text: String -> copyToClipboard(text) } }
}

