/**
 * Payload for a `SNAPSHOT_UPDATE` [Event] — carries the freshly captured
 * `Snapshot` so subscribers (the chart screen, downstream filters and
 * triggers) can update without an additional `/data/series` round-trip.
 */
package krill.zone.shared.events

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.Snapshot

/** New snapshot captured by a DataPoint. */
@Serializable
data class SnapshotUpdatedEventPayload(val snapshot: Snapshot) : EventPayload
