/**
 * Metadata for a `Server.LLM` node — the entry point for Krill's LLM
 * integration. Holds the upstream model identifier, the connection details,
 * the persisted chat history, and the user-selected nodes the model should
 * have visibility into.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*

/**
 * Payload for a `Server.LLM` node.
 */
@Serializable
data class LLMMetaData(
    /** Port on the server hosting the upstream Ollama-compatible inference endpoint. */
    val port: Int = 11434,
    /** Model identifier sent on every request (e.g. `"kimi-k2:latest"`). */
    val model: String = "qwen2.5-coder:32b-instruct-q8_0",
    /** Persisted conversation history. The most recent message is at the end. */
    val chat: List<Message> = emptyList(),
    /** user entered prompt */
    val prompt: String = "",

    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData
