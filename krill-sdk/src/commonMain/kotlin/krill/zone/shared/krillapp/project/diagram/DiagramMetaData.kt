/**
 * Metadata for a `Diagram` node — an SVG dashboard owned by a Project. The
 * SVG itself lives at the server's `/project/<id>/diagram/<filename>` URL;
 * this metadata holds the link and the bindings that overlay live node state
 * on top of the diagram's `k_*` anchors.
 */
package krill.zone.shared.krillapp.project.diagram

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.InvocationTrigger
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

/**
 * Payload for a `Diagram` node.
 */
@Serializable
data class DiagramMetaData(
    /** Display name shown on the project chip and in the editor. */
    val name: String = "Diagram",
    /** Optional free-form description shown in the editor's info pane. */
    val description: String = "",
    /** Filename of the SVG inside the server's diagram directory. */
    val source: String = "",
    /**
     * Mapping from SVG anchor id (`k_*` data attribute on an SVG element) to
     * the node id whose live state should be overlaid on that element.
     */
    val anchorBindings: Map<String, String> = emptyMap(),
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData
