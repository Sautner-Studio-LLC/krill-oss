/**
 * Compact host description fetched from a Krill server's `/info` endpoint
 * during onboarding. Shown in the FTUE flow so the user can confirm they're
 * connecting to the right device before committing the PIN.
 *
 * Wire-only type — does not implement [krill.zone.shared.node.NodeMetaData]
 * and is never persisted to a node; it just travels in HTTP responses.
 */
package krill.zone.shared.krillapp.server

import kotlinx.serialization.*

/**
 * Hardware / OS summary returned by a server's identity probe.
 *
 * `kernel` is empty by default because most platforms (macOS, Windows)
 * don't surface a meaningful kernel string separately from [os] — only the
 * Pi-style Linux servers populate it.
 */
@Serializable
data class ServerInfo(val model: String, val os: String, val kernel: String = "")
