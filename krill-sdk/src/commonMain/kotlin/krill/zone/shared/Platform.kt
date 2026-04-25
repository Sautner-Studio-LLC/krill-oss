/**
 * Wire-level identifier for the runtime environment a Krill client or node is
 * executing in. Travels inside [krill.zone.shared.node.NodeWire] so the server
 * (and other peers) can render the right icon for a peer in the swarm UI and
 * gate platform-specific features (e.g. GPIO is only meaningful for
 * `RASPBERRY_PI`).
 *
 * The companion `expect val installId`, `expect val hostName`, and
 * `expect val platform` declarations remain in the consuming module — they are
 * platform-actual lookups, not pure value types, and not part of the SDK.
 */
package krill.zone.shared

import kotlinx.serialization.*

/**
 * The set of runtime environments a Krill participant can advertise.
 *
 * `@Serializable` because the value rides inside multicast beacons and HTTP
 * responses. Ordinals and names are part of the wire contract — adding a new
 * entry is safe (older readers will fail to deserialize, which is the desired
 * fail-loud behaviour); reordering or renaming existing entries is **not**.
 */
@Serializable
enum class Platform {
    /** Apple iOS / iPadOS — Compose Multiplatform iOS target. */
    IOS,

    /** Android — both phone/tablet and Android-on-Pi installations. */
    ANDROID,

    /** Desktop JVM — macOS, Linux, Windows running the Compose desktop client. */
    DESKTOP,

    /** Browser — the wasmJs Compose target served by the Krill server. */
    WASM,

    /** A Krill server running on a Raspberry Pi (with GPIO/PWM/I2C access via krill-pi4j). */
    RASPBERRY_PI,

    /** A Krill server running headless on a non-Pi host (no hardware GPIO). */
    HEADLESS_SERVER,

    /** Fallback for environments that have not yet identified themselves. */
    UNKNOWN,
}
