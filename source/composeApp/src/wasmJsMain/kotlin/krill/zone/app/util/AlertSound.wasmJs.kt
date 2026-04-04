@file:OptIn(ExperimentalWasmJsInterop::class)

package krill.zone.app.util

import kotlin.js.ExperimentalWasmJsInterop

private fun playBeep(): JsAny? = js("""
(function() {
    try {
        var a = new AudioContext();
        var o = a.createOscillator();
        o.frequency.value = 880;
        o.connect(a.destination);
        o.start();
        setTimeout(function() { o.stop(); a.close(); }, 200);
    } catch(e) {}
    return null;
})()
""")

actual fun platformAlertBeep() {
    try {
        playBeep()
    } catch (_: Throwable) {
        // Browser may block AudioContext without user gesture
    }
}
