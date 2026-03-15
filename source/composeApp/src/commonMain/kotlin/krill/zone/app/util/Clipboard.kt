package krill.zone.app.util

import androidx.compose.runtime.*

/**
 * Copies the given text to the system clipboard.
 * This is a platform-specific implementation.
 */
expect fun copyToClipboard(text: String)

/**
 * Provides access to platform clipboard for composable functions.
 * Returns a function that can be called to copy text to clipboard.
 */
@Composable
expect fun rememberClipboardCopier(): (String) -> Unit

