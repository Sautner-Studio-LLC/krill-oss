/**
 * Top-level structured reply from the LLM agent. Parsed by the Krill server
 * from the assistant's chat-message body — the agent is prompted to emit
 * JSON conforming to this schema rather than free-form prose, so the server
 * can route the reply (validate actions, ask clarifying questions, fetch
 * more context) without further NLP.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Structured agent response.
 */
@Serializable
data class LLMResponse(
    /** Disposition of the reply — see [LLMResponseStatus]. */
    val status: LLMResponseStatus,
    /** Plain-language summary shown to the user in the chat panel. */
    val userMessage: String,
    /** Existing nodes the user / server should review before executing actions. */
    val nodesToReview: List<LLMNodeReview> = emptyList(),
    /** Ordered plan of actions for the server to validate and execute. */
    val proposedActions: List<LLMProposedAction> = emptyList(),
    /**
     * When [status] is [LLMResponseStatus.NEEDS_CONTEXT], names of the
     * KrillApp types whose feature contracts should be added to the next
     * round-trip (e.g. `"KrillApp.Executor.LogicGate"`).
     */
    val additionalContextRequest: List<String> = emptyList(),
    /** Follow-up questions the agent wants the user to answer. */
    val questions: List<String> = emptyList(),
    /** Warnings about side effects, safety concerns, or ambiguity in the plan. */
    val warnings: List<String> = emptyList(),
)
