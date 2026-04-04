package krill.zone.app.util

/**
 * Plays a short alert beep sound. Platform-specific implementation.
 * On platforms where audio is not supported or blocked by autoplay policies, this is a no-op.
 */
expect fun platformAlertBeep()
