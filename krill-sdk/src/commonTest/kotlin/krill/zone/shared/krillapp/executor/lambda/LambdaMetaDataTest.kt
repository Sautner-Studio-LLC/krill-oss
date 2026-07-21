package krill.zone.shared.krillapp.executor.lambda

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the per-Lambda-node `allowNetwork` opt-in (issue #213).
 *
 * Covers:
 *  - `allowNetwork` defaults to `false` (restrictive posture per krill#918).
 *  - Old wire payloads missing `allowNetwork` still deserialize, defaulting to `false`.
 *  - `LambdaMetaData` round-trips through JSON with `allowNetwork` set.
 */
class LambdaMetaDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `LambdaMetaData defaults allowNetwork to false`() {
        val meta = LambdaMetaData()
        assertFalse(meta.allowNetwork)
    }

    @Test
    fun `LambdaMetaData back-compat round-trip ignores missing allowNetwork`() {
        val oldPayload = """
            {
              "name": "average",
              "filename": "average.py",
              "timestamp": 0,
              "error": "",
              "sources": [],
              "snapshot": {"timestamp": 0, "value": ""},
              "invocationTriggers": [],
              "nodeAction": "EXECUTE",
              "inputs": []
            }
        """.trimIndent()

        val meta = json.decodeFromString<LambdaMetaData>(oldPayload)

        assertEquals("average.py", meta.filename)
        assertFalse(meta.allowNetwork)
    }

    @Test
    fun `LambdaMetaData round-trips with allowNetwork set`() {
        val original = LambdaMetaData(
            name = "external-api-call",
            filename = "call_api.py",
            allowNetwork = true,
        )
        val encoded = json.encodeToString(LambdaMetaData.serializer(), original)
        val decoded = json.decodeFromString<LambdaMetaData>(encoded)
        assertEquals(original, decoded)
        assertTrue(decoded.allowNetwork)
    }
}
