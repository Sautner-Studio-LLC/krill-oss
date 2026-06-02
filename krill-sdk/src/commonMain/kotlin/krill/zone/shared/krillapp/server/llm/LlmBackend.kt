package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/** Inference backends a `Server.LLM` node can route requests to. */
@Serializable
enum class LlmBackend(val displayLabel: String) {
    /** Local Ollama daemon (default). */
    OLLAMA("Ollama"),

    /**
     * Any OpenAI-compatible endpoint (LM Studio, vLLM, etc.).
     * Authentication is handled by local proxy — no bearer field needed.
     */
    OPENAI_COMPATIBLE("OpenAI-compatible"),
}
