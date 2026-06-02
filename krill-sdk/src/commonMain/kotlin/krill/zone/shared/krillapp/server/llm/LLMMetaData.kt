/**
 * Metadata for a `Server.LLM` node — a single-purpose, source-invoked LLM
 * transform node. Holds the upstream model identifier, connection details,
 * backend selection, and output-format contract. Chat-history is not persisted
 * here; each invocation is stateless from the node's perspective.
 *
 * All new fields default so that existing serialized payloads round-trip
 * without error. The removed `chat` field is silently ignored on read because
 * the project-wide JSON config sets `ignoreUnknownKeys = true`.
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
    /** Port on the server hosting the inference endpoint. */
    val port: Int = 11434,
    /** Model identifier sent on every request (e.g. `"qwen2.5-coder:32b-instruct-q8_0"`). */
    val model: String = "qwen2.5-coder:32b-instruct-q8_0",
    /** User-entered prompt template; injected into the request at invocation time. */
    val prompt: String = "",
    /** Inference backend this node routes requests to. */
    val backend: LlmBackend = LlmBackend.OLLAMA,
    /**
     * System prompt prepended to every request.
     * Blank means the server applies its default Krill persona automatically.
     */
    val systemPrompt: String = "",
    /** How the model should format its reply. */
    val responseFormat: ResponseFormat = ResponseFormat.NATURAL_LANGUAGE,
    /**
     * JSON Schema (or natural-language instruction) the model must follow when
     * [responseFormat] is [ResponseFormat.JSON].
     * Defaults to [LLMResult.JSON_SCHEMA] so observer nodes can decode
     * `snapshot.value` as [LLMResult] out of the box.
     */
    val responseInstructions: String = LLMResult.JSON_SCHEMA,

    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData
