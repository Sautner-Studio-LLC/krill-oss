/**
 * Metadata for the `Spacer` node — a layout-only filler used in the swarm UI
 * to add visual breathing room between sibling nodes. Has no runtime
 * behaviour; never executes, never persists snapshots.
 */
package krill.zone.shared.krillapp.spacer

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

/**
 * Payload for a `Spacer` node. Carries only a display [name] (so the user
 * can label the gap) and the standard error field.
 */
@Serializable
data class SpacerMetaData(val name: String = "spacer", override val error: String = "") : NodeMetaData
