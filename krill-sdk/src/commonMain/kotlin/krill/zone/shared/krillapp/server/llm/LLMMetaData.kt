/**
 * Metadata for a `Server.LLM` node — the entry point for Krill's LLM
 * integration. Holds the upstream model identifier, the connection details,
 * the persisted chat history, and the user-selected nodes the model should
 * have visibility into.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Server.LLM` node.
 */
@Serializable
data class LLMMetaData(
    /** Port on the server hosting the upstream Ollama-compatible inference endpoint. */
    val port: Int = 11434,
    /** Model identifier sent on every request (e.g. `"kimi-k2:latest"`). */
    val model: String = "kimi-k2:latest",
    /** Persisted conversation history. The most recent message is at the end. */
    val chat: List<Message> = emptyList(),
    /**
     * Nodes the user has explicitly tagged as in-scope for the model — the
     * server includes their state in the prompt context so the model can
     * reason about them.
     */
    val selectedNodes: List<NodeIdentity> = emptyList(),
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
