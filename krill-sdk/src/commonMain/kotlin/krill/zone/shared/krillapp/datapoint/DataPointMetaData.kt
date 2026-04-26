/**
 * Metadata for a `DataPoint` node — Krill's primary unit of observable state.
 * Each DataPoint owns a single [Snapshot] (its current value) and a
 * configured [DataType] that drives how the value is interpreted by filters,
 * triggers, graphs, and the editor UI.
 */
package krill.zone.shared.krillapp.datapoint

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData
import kotlin.time.*

/**
 * Payload for a `DataPoint` node.
 */
@Serializable
data class DataPointMetaData @OptIn(ExperimentalTime::class) constructor(
    /** Display name; default uses an epoch-second suffix so freshly created points don't collide. */
    val name: String = "data-point-${Clock.System.now().epochSeconds}",
    /**
     * Optional UUID of a node whose values this DataPoint mirrors —
     * empty when the point is a primary source rather than a derived one.
     */
    val sourceId: String = "",
    /** Most recent observation. Defaults to a "now, 0.0" placeholder so graphs render cleanly. */
    val snapshot: Snapshot = Snapshot(Clock.System.now().epochSeconds, 0.0),
    /** Number of decimal places the editor renders [Snapshot.value] with. */
    val precision: Int = 2,
    /** Free-form units string (`"°C"`, `"%"`, `"V"`) appended to display values. */
    val unit: String = "",
    /** `true` when the user can type values into the editor — `false` for read-only sources. */
    val manualEntry: Boolean = true,
    /** Drives interpretation of [Snapshot.value]; see [DataType]. */
    val dataType: DataType = DataType.DOUBLE,
    /** Maximum age (epoch millis) of stored snapshots — `0` means keep forever. */
    val maxAge: Long = 0,
    /** Optional path-like address for grouping points in the editor. */
    val path: String = "",
    override val error: String = "",
) : NodeMetaData
