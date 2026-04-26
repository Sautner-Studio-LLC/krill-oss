/**
 * Top-level status of an [LLMResponse] — tells the Krill server what to do
 * next with the agent's reply: validate and execute the proposed actions,
 * loop back to the user for clarification, fetch more context, or surface a
 * "cannot complete" message.
 *
 * `@SerialName` is set on each entry to a snake_case form because the
 * upstream agent emits these as JSON literals and Kotlin defaults would
 * yield the (mismatched) UPPER_SNAKE form.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Disposition of an LLM agent's reply.
 */
@Serializable
enum class LLMResponseStatus {
    /** Plan is complete and actionable; server should validate and execute. */
    @SerialName("ready")
    READY,

    /** Agent needs the user to answer follow-up questions before proceeding. */
    @SerialName("needs_clarification")
    NEEDS_CLARIFICATION,

    /** Agent needs additional [krill.zone.shared.feature.KrillFeature] contracts not in context. */
    @SerialName("needs_context")
    NEEDS_CONTEXT,

    /** Request cannot be fulfilled with the available node types or context. */
    @SerialName("cannot_complete")
    CANNOT_COMPLETE,
}
