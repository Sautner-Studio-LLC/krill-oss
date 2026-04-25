/**
 * Metadata for a `Diagram` node — an SVG dashboard owned by a Project. The
 * SVG itself lives at the server's `/project/<id>/diagram/<filename>` URL;
 * this metadata holds the link and the bindings that overlay live node state
 * on top of the diagram's `k_*` anchors.
 */
package krill.zone.shared.krillapp.project.diagram

import kotlinx.serialization.*
import krill.zone.shared.node.NodeMetaData

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
) : NodeMetaData
