/**
 * Metadata for the `Button` trigger — a UI-only node whose only execution
 * pathway is a manual click in the swarm editor. Used as a low-friction way
 * to wire ad-hoc actions without committing to a more elaborate trigger
 * (e.g., to fire a one-off webhook executor during testing).
 */
package krill.zone.shared.krillapp.trigger.button

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

/** Payload for a `Button` trigger — display name plus the standard error field. */
@Serializable
data class ButtonMetaData(
    val name: String = "button",
    override val error: String = "",
) : NodeMetaData
