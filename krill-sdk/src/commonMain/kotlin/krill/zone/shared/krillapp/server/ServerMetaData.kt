/**
 * Metadata for a `Server` node — every Krill server (and its peer
 * representations) advertises one of these. Carries the network identity
 * (`name`, `port`, `isLocal`), the host description (`model`, `version`,
 * `os`, `platform`), and lightweight inventory (`nodes`, `serialDevices`,
 * `cameraAvailable`) so a fresh client can render a meaningful server tile
 * without round-tripping to the server first.
 *
 * Authentication is PIN-derived (see [krill.zone.shared.security.PinDerivation])
 * — the previously-stored `apiKey` and `peerApiKey` fields have been removed.
 * Old persisted records are tolerated because the project-wide `fastJson`
 * is configured with `ignoreUnknownKeys = true`.
 */
package krill.zone.shared.krillapp.server

import kotlinx.serialization.*
import krill.zone.shared.Platform
import krill.zone.shared.node.NodeMetaData

/**
 * Payload for a `Server` (or `Server.Peer`) node.
 */
@Serializable
data class ServerMetaData(
    /** Display name — typically the host's network hostname. */
    val name: String = "",
    /** TCP port the server's HTTP/SSE endpoint is listening on. */
    val port: Int = 0,
    /** Hardware model description (e.g. `"Raspberry Pi 5"`, `"MacBook Pro"`). */
    val model: String = "",
    /** Krill server version string (mirrors `version.txt`). */
    val version: String = "",
    /** OS identifier (e.g. `"Linux"`, `"macOS 14.4"`). */
    val os: String = "",
    /** Runtime environment of the server — drives the icon shown in the swarm UI. */
    val platform: Platform? = null,

    /**
     * `true` if the server was discovered via LAN multicast beacon. Used to
     * decide whether to append `.local` to [name] when constructing reachable
     * URLs (see [resolvedHost]).
     */
    val isLocal: Boolean = false,

    /** `true` while verbose logging has been enabled on the server side. */
    val loggingEnabled: Boolean = false,
    /** UUIDs of nodes hosted by this server — surfaced for fast counts in the UI. */
    val nodes: List<String> = emptyList(),
    /** Serial device hardware ids the server is exposing via `Server.SerialDevice` nodes. */
    val serialDevices: List<String> = emptyList(),
    /** `true` if the server has a camera attached and exposes the `/camera/` routes. */
    val cameraAvailable: Boolean = false,
    override val error: String = "",
) : NodeMetaData {

    /**
     * Returns a resolvable hostname for this server.
     *
     *  * Already-FQDN hostnames (have a dot) are returned as-is.
     *  * LAN-discovered servers get `.local` appended (mDNS).
     *  * Bare hostnames without a dot are *inferred* to be LAN-mDNS and
     *    also get `.local` appended — this catches legacy stored records
     *    that predate the [isLocal] field.
     */
    fun resolvedHost(): String {
        if (name.endsWith(".local")) return name
        if (isLocal) return "$name.local"
        if (name.isNotEmpty() && !name.contains('.')) return "$name.local"
        return name
    }

    /** Returns the full `https://<resolvedHost>:<port>` base URL for this server. */
    fun getUrl(): String = "https://${resolvedHost()}:$port"
}
