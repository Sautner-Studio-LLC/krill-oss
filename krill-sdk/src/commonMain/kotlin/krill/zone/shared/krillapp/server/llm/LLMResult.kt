package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Default JSON output contract for a `Server.LLM` node configured with
 * [ResponseFormat.JSON].
 *
 * Observer nodes (e.g. a DataPoint child) can decode `snapshot.value` into this
 * class and read [value] / [label] / [confidence] without knowing the upstream
 * prompt. The [JSON_SCHEMA] constant is the default value for
 * [LLMMetaData.responseInstructions] so the server can embed it in the system
 * prompt automatically.
 */
@Serializable
data class LLMResult(
    /** One-sentence human-readable summary of the result. */
    val summary: String = "",
    /** Primary numeric result, or `null` when the answer is non-numeric. */
    val value: Double? = null,
    /** Short label classifying the result (e.g. `"HIGH"`, `"normal"`). */
    val label: String? = null,
    /** Model's self-reported confidence in [0, 1], or `null` if not applicable. */
    val confidence: Double? = null,
    /** Extended explanation or reasoning; empty string when not needed. */
    val detail: String = "",
) {
    companion object {
        /**
         * JSON Schema string describing [LLMResult].  Used as the default value
         * of [LLMMetaData.responseInstructions] so the server-side processor can
         * append it to the system prompt without hard-coding it twice.
         */
        const val JSON_SCHEMA: String = """{"type":"object","properties":{"summary":{"type":"string"},"value":{"type":"number","nullable":true},"label":{"type":"string","nullable":true},"confidence":{"type":"number","minimum":0,"maximum":1,"nullable":true},"detail":{"type":"string"}},"required":["summary","detail"]}"""
    }
}
