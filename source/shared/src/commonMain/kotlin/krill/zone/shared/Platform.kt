package krill.zone.shared

expect val installId: () -> String

expect val hostName: String

expect val platform: Platform


/** Platforms with pointer/mouse support that can detect right-click */
val supportsRightClick = platform == Platform.DESKTOP || platform == Platform.WASM

val isMobile = platform == Platform.IOS || platform == Platform.ANDROID
