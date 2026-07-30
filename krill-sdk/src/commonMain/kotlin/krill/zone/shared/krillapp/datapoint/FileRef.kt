/**
 * Claim-check reference to a file's bytes, used as the [Snapshot.value] payload
 * for [DataType.FILE] snapshots.
 *
 * Krill's state spine (DB, SSE, mirrors) never carries raw file bytes — that
 * would balloon every hop and break down over asymmetric WAN links. Instead a
 * `FILE` snapshot stores a serialized [FileRef] (content hash + size + mime +
 * owning host), and the bytes themselves live in a per-server blob store that
 * consumers pull from lazily, on demand, from whichever winner needs them.
 */
package krill.zone.shared.krillapp.datapoint

import kotlinx.serialization.*

/**
 * Content-addressed pointer to a file's bytes hosted by a Krill server.
 *
 * Serialized into [Snapshot.value] rather than carried as a first-class
 * `Node` field, so any existing `FILE`-typed DataPoint or swarm-work result
 * can hold one without a schema change.
 */
@Serializable
data class FileRef(
    /** SHA-256 content address of the file's bytes. */
    val hash: String,
    /** Size of the referenced bytes, in bytes. */
    val sizeBytes: Long,
    /** MIME type of the referenced bytes (e.g. `"image/png"`, `"text/plain"`). */
    val mime: String,
    /** `installId` of the Krill server that owns the bytes and serves them on demand. */
    val host: String,
    /** Optional human-readable filename, for display purposes only. */
    val name: String = "",
)
