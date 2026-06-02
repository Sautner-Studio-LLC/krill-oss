package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/** Governs how a `Server.LLM` node formats its output. */
@Serializable
enum class ResponseFormat(val displayLabel: String) {
    /** Free-form prose from the model (default). */
    NATURAL_LANGUAGE("Natural Language"),

    /**
     * Structured JSON output.  The model is instructed (via [LLMMetaData.responseInstructions])
     * to emit a JSON object matching the declared schema; [LLMResult] is the default contract.
     */
    JSON("JSON"),
}
