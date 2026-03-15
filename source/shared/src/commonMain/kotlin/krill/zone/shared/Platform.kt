package krill.zone.shared

import kotlinx.serialization.*

expect val installId: () -> String

expect val hostName: String

expect val platform: Platform

@Serializable
enum class Platform {
    IOS,
    ANDROID,
    DESKTOP,

    WASM,
    RASPBERRY_PI,
    HEADLESS_SERVER,

    UNKNOWN
}


/** Platforms with pointer/mouse support that can detect right-click */
val supportsRightClick = platform == Platform.DESKTOP || platform == Platform.WASM

val isMobile = platform == Platform.IOS || platform == Platform.ANDROID
