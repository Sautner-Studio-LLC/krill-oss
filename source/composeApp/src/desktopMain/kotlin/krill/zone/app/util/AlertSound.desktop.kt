package krill.zone.app.util

actual fun platformAlertBeep() {
    try {
        java.awt.Toolkit.getDefaultToolkit().beep()
    } catch (_: Exception) {
        // Headless environments may not support beep
    }
}
