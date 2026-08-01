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
    /**
     * User-chosen name for this node, surfaced on the canvas. Empty falls back
     * to the type string (`LLM`), which is what every LLM node showed before
     * this field existed — two LLM nodes on one canvas were indistinguishable.
     */
    val name: String = "",
    /** Port on the server hosting the inference endpoint. */
    val port: Int = 11434,
    /** Model identifier sent on every request (e.g. `"qwen3-coder-next:32k"`). */
    val model: String = "",
    /**
     * System prompt prepended to every request.
     * Blank means the server applies its default Krill persona automatically.
     */
    val systemPrompt: String = "",
    /** User-entered prompt template; injected into the request at invocation time. */
    val prompt: String = "",
    /** Inference backend this node routes requests to. */
    val backend: LlmBackend = LlmBackend.OLLAMA,

    val systemPromptDataSource: NodeIdentity? = null,

    val promptDataSource: NodeIdentity? = null,

    /** How the model should format its reply. */
    val responseFormat: ResponseFormat = ResponseFormat.NATURAL_LANGUAGE,
    /**
     * JSON Schema (or natural-language instruction) the model must follow when
     * [responseFormat] is [ResponseFormat.JSON].
     * Defaults to [LLMResult.JSON_SCHEMA] so observer nodes can decode
     * `snapshot.value` as [LLMResult] out of the box.
     */
    val responseInstructions: String = LLMResult.JSON_SCHEMA,
    /**
     * Ollama `options.num_ctx` sent on every request — the context window
     * (in tokens) the backend allocates KV cache for. 8192 is a safe default
     * that fits every krill workload seen so far without risking a KV-cache
     * OOM on typical hardware (see `krill#883`).
     */
    val numCtx: Int = 8192,
    /** Ollama `options.temperature` sent on every request. Null lets the backend use its own default. */
    val temperature: Double? = null,
    /** Ollama `options.keep_alive` sent on every request. Null lets the backend use its own default. */
    val keepAlive: String? = null,

    /**
     * Opt-in to advertising this node as a swarm-work claimant. Defaults to
     * `false` — an LLM node only participates in `Swarm.Work` dispatch once
     * its owner explicitly enables it.
     */
    val swarmEnabled: Boolean = false,
    /** Models this node advertises as available; an empty list defaults to `[model]`. */
    val advertisedModels: List<String> = emptyList(),
    /** Coarse hardware class advertised alongside availability (`"8g"`, `"24g"`, `"80g"`, `"apple-m"`). */
    val vramClass: String = "",
    /** Unitless advertised cost, lower is cheaper; `-1` means "not advertising". */
    val costScore: Double = -1.0,
    /** Node that supplied [costScore], for provenance when it isn't self-reported. */
    val costScoreSource: NodeIdentity? = null,
    /** Node that supplied the accept-window policy in effect for this advertisement, if any. */
    val acceptWindowSource: NodeIdentity? = null,
    /** Epoch millis this node last (re)computed and published its availability block. */
    val advertisedAt: Long = 0,

    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() =   model.take(16).ifEmpty { name }
}
