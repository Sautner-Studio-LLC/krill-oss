package krill.zone.shared.lifecycle

import kotlinx.coroutines.flow.*

/**
 * Observable app lifecycle state — foreground/background.
 * Platform implementations detect OS-level app state transitions.
 * Consumers (beacon supervisor, graph polling, SSE) check isForeground
 * to pause work when the app is not visible.
 */
object AppLifecycle {
    private val _isForeground = MutableStateFlow(true)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    fun onForeground() {
        _isForeground.value = true
    }

    fun onBackground() {
        _isForeground.value = false
    }
}
