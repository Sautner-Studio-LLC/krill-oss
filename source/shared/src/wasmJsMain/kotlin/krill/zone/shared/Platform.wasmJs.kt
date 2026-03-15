package krill.zone.shared

import kotlinx.browser.*

actual val installId: () -> String
    get() = { "${window.location.hostname}:${window.location.port.toInt()}" }
actual val hostName: String
    get() = window.location.hostname

actual val platform: Platform
    get() = Platform.WASM

