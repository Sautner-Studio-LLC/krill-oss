package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for the LLMMetaData single-purpose model alignment (issue #112).
 *
 * Covers:
 *  - New fields ([LlmBackend], [ResponseFormat], systemPrompt, responseInstructions) default correctly.
 *  - Old wire payloads that include the removed `chat` field still deserialize without error.
 *  - [LLMResult] round-trips through JSON.
 *  - [LLMResult.JSON_SCHEMA] is non-empty and constant.
 */
class LLMMetaDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `LLMMetaData defaults to OLLAMA backend and NATURAL_LANGUAGE format`() {
        val meta = LLMMetaData()
        assertEquals(LlmBackend.OLLAMA, meta.backend)
        assertEquals(ResponseFormat.NATURAL_LANGUAGE, meta.responseFormat)
        assertEquals("", meta.systemPrompt)
        assertEquals(LLMResult.JSON_SCHEMA, meta.responseInstructions)
        assertEquals(8192, meta.numCtx)
        assertNull(meta.temperature)
        assertNull(meta.keepAlive)
    }

    @Test
    fun `LLMMetaData back-compat round-trip ignores missing numCtx temperature keepAlive`() {
        val oldPayload = """
            {
              "port": 11434,
              "model": "old-model",
              "prompt": "summarise",
              "error": "",
              "sources": [],
              "snapshot": {"timestamp": 0, "value": ""},
              "invocationTriggers": [],
              "nodeAction": "EXECUTE",
              "inputs": []
            }
        """.trimIndent()

        val meta = json.decodeFromString<LLMMetaData>(oldPayload)

        assertEquals(8192, meta.numCtx)
        assertNull(meta.temperature)
        assertNull(meta.keepAlive)
    }

    @Test
    fun `LLMMetaData round-trips with numCtx temperature keepAlive`() {
        val original = LLMMetaData(
            model = "llama3:8b",
            numCtx = 32768,
            temperature = 0.7,
            keepAlive = "5m",
        )
        val encoded = json.encodeToString(LLMMetaData.serializer(), original)
        val decoded = json.decodeFromString<LLMMetaData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `LLMMetaData back-compat round-trip ignores removed chat field`() {
        val oldPayload = """
            {
              "port": 11434,
              "model": "old-model",
              "chat": [{"content": "hello", "role": "user"}],
              "prompt": "summarise",
              "error": "",
              "sources": [],
              "snapshot": {"timestamp": 0, "value": ""},
              "invocationTriggers": [],
              "nodeAction": "EXECUTE",
              "inputs": []
            }
        """.trimIndent()

        val meta = json.decodeFromString<LLMMetaData>(oldPayload)

        assertEquals(11434, meta.port)
        assertEquals("old-model", meta.model)
        assertEquals("summarise", meta.prompt)
        // new fields get their defaults
        assertEquals(LlmBackend.OLLAMA, meta.backend)
        assertEquals(ResponseFormat.NATURAL_LANGUAGE, meta.responseFormat)
        assertEquals("", meta.systemPrompt)
    }

    @Test
    fun `LLMMetaData round-trips with new fields`() {
        val original = LLMMetaData(
            model = "llama3:8b",
            backend = LlmBackend.OPENAI_COMPATIBLE,
            systemPrompt = "You are a sensor analyst.",
            responseFormat = ResponseFormat.JSON,
            responseInstructions = LLMResult.JSON_SCHEMA,
        )
        val encoded = json.encodeToString(LLMMetaData.serializer(), original)
        val decoded = json.decodeFromString<LLMMetaData>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `LLMResult defaults are all empty or null`() {
        val result = LLMResult()
        assertEquals("", result.summary)
        assertNull(result.value)
        assertNull(result.label)
        assertNull(result.confidence)
        assertEquals("", result.detail)
    }

    @Test
    fun `LLMResult round-trips through JSON`() {
        val original = LLMResult(summary = "temp is high", value = 42.5, label = "HIGH", confidence = 0.9, detail = "exceeds 40C threshold")
        val encoded = json.encodeToString(LLMResult.serializer(), original)
        val decoded = json.decodeFromString<LLMResult>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `LLMResult JSON_SCHEMA constant is non-empty`() {
        assertTrue(LLMResult.JSON_SCHEMA.isNotBlank(), "JSON_SCHEMA must not be blank")
        assertTrue(LLMResult.JSON_SCHEMA.contains("summary"), "JSON_SCHEMA must reference 'summary'")
    }
}
