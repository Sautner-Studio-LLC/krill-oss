/**
 * Fluent builder for [Node] instances, used wherever a node is constructed
 * from the outside (FTUE, SSE wire reception, server-side seeding, the
 * editor's "add a child" flow). Centralising construction here means the
 * defaulting rules (random UUID for missing ids, `Server` parent points at
 * itself) live in one place rather than being duplicated at every call site.
 */
package krill.zone.shared.node

import krill.zone.shared.KrillApp
import kotlin.uuid.*

/**
 * Mutable builder for a single [Node].
 *
 * Usage:
 * ```
 * val node = NodeBuilder()
 *     .type(KrillApp.Server.Pin)
 *     .host(serverId)
 *     .parent(serverId)
 *     .meta(PinMetaData(name = "fan"))
 *     .create()
 * ```
 *
 * `id` is auto-generated as a random UUID when omitted; on a `Server`-typed
 * node, [parent] auto-fills to `id` because top-level servers are their own
 * parents in the swarm graph.
 */
class NodeBuilder {

    var id: String? = null
    var parent: String? = null
    var type: KrillApp? = null
    private var meta: NodeMetaData? = null
    private var host: String? = null

    private var state: NodeState = NodeState.NONE

    fun id(id: String) = apply { this.id = id }
    fun parent(parent: String) = apply { this.parent = parent }
    fun type(type: KrillApp) = apply { this.type = type }
    fun meta(meta: NodeMetaData) = apply { this.meta = meta }
    fun host(host: String) = apply { this.host = host }
    fun state(state: NodeState) = apply { this.state = state }

    /**
     * Seeds the builder from an existing [Node] — useful when callers want
     * to alter only one or two fields and round-trip the rest.
     */
    fun node(node: Node) = apply {
        this.id = node.id
        this.type = node.type
        this.parent = node.parent
        this.host = node.host
        this.meta = node.meta
        this.state = node.state
    }

    /**
     * Builds the [Node].
     *
     * - `id` defaults to `Uuid.random()` if not set.
     * - `parent` is auto-filled to `id` when the node is a [KrillApp.Server]
     *   and no parent was supplied (top-level servers are their own parents).
     * - `host`, `type`, and `meta` are required and throw [NullPointerException]
     *   if missing — this is intentional fail-loud behaviour rather than
     *   silently constructing a half-built node.
     */
    @ExperimentalUuidApi
    fun create(): Node {
        val checkedId = this.id ?: Uuid.random().toString()
        if (type is KrillApp.Server && parent == null) {
            parent = checkedId
        }

        return Node(
            id = checkedId,
            parent = parent ?: throw NullPointerException("Parent must not be null"),
            host = host ?: throw NullPointerException("Host must not be null"),
            type = type ?: throw NullPointerException("Node Type must not be null"),
            state = state,
            meta = meta ?: throw NullPointerException("Node Meta Data must not be null"),
        )
    }
}
