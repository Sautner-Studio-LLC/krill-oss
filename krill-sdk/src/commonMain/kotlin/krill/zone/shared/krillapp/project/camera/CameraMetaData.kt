/**
 * Metadata for a `Camera` node — a server-attached video source whose JPEG
 * snapshots and live MJPEG stream are exposed via the server's
 * `/camera/<id>/...` endpoints.
 */
package krill.zone.shared.krillapp.project.camera

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

/**
 * Payload for a `Camera` node — the editable capture configuration and an
 * `enabled` toggle.
 */
@Serializable
data class CameraMetaData(
    /** Display name; empty string falls back to "Camera" in the UI. */
    val name: String = "",
    /** Capture resolution, formatted as `"<width>x<height>"`. */
    val resolution: String = "1280x720",
    /** Target frames per second for the MJPEG stream. */
    val framerate: Int = 15,
    /** Image rotation in degrees applied before encoding (0 / 90 / 180 / 270). */
    val rotation: Int = 0,
    /** TCP port on the server that exposes the live stream. */
    val streamPort: Int = 8443,
    /** When `false`, the server stops capturing to save resources. */
    val enabled: Boolean = true,
    override val error: String = "",
) : NodeMetaData
