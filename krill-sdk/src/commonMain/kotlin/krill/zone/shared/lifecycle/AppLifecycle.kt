/**
 * Cross-platform notion of "is the host app currently in the foreground?"
 *
 * Krill consumers (the beacon supervisor, graph-screen polling, the SSE
 * reconnect timer) use this to pause work when the user is no longer looking,
 * which on mobile saves battery and on desktop avoids hammering the server
 * with traffic the user can't see.
 *
 * Platform integration code is responsible for calling [AppLifecycle.onForeground] and
 * [AppLifecycle.onBackground] in response to OS-level lifecycle events:
 *
 *  * Android — `Lifecycle.Event.ON_START` / `ON_STOP` from `ProcessLifecycleOwner`.
 *  * iOS — `UIApplicationWillEnterForegroundNotification` / `UIApplicationDidEnterBackgroundNotification`.
 *  * Desktop — Compose `Window` focus / minimise events.
 *  * wasmJs — `document.visibilitychange`.
 *
 * The default state is [isForeground] = `true`, so apps that never wire up
 * lifecycle events still see "always foreground" rather than getting stuck in
 * a no-work background mode.
 */
package krill.zone.shared.lifecycle

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton holding observable foreground / background state.
 *
 * Singleton (rather than DI-injected) because every consumer in the app needs
 * to observe the same value, and there is exactly one OS-level lifecycle per
 * process.
 */
object AppLifecycle {
    private val _isForeground = MutableStateFlow(true)

    /**
     * `true` while the host app is foregrounded / visible. Observers should
     * pause discretionary work whenever this flips to `false`.
     */
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    /** Called by the platform integration when the app becomes foregrounded. */
    fun onForeground() {
        _isForeground.value = true
    }

    /** Called by the platform integration when the app is sent to background. */
    fun onBackground() {
        _isForeground.value = false
    }
}
