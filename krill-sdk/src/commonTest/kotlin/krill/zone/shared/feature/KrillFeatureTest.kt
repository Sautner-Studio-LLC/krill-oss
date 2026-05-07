package krill.zone.shared.feature

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Regression tests for issue Sautner-Studio-LLC/krill-oss#65 / krill#241.
 *
 * `requiresServer` is intentionally declared without a default so that any
 * `KrillApp.*.json` resource missing the flag fails loud at deserialise time
 * rather than silently defaulting to a wrong gating decision in the FTUE /
 * recipe layer.
 */
class KrillFeatureTest {

    @Test
    fun `requiresServer round-trips for a server-dependent feature`() {
        val json = featureJson(requiresServer = true)
        val feature = Json.decodeFromString(KrillFeature.serializer(), json)
        assertEquals(true, feature.requiresServer)
    }

    @Test
    fun `requiresServer round-trips for a client-side feature`() {
        val json = featureJson(requiresServer = false)
        val feature = Json.decodeFromString(KrillFeature.serializer(), json)
        assertEquals(false, feature.requiresServer)
    }

    @Test
    fun `missing requiresServer key fails loud on deserialise`() {
        val json = featureJson(requiresServer = null)
        assertFailsWith<SerializationException> {
            Json.decodeFromString(KrillFeature.serializer(), json)
        }
    }

    private fun featureJson(requiresServer: Boolean?): String {
        val requiresServerLine = requiresServer?.let { "\"requiresServer\": $it," } ?: ""
        return """
            {
              "category": "Server",
              "description": "",
              "llmActsOnExternalWorld": false,
              "llmBehavior": [],
              "llmCanCreateChildren": false,
              "llmConnectionHints": {
                "role": ""
              },
              "llmCreationHints": "",
              "llmExamples": [],
              "llmExecutesChildren": false,
              "llmInputs": [],
              "llmOutputs": [],
              "llmPromptHints": [],
              "llmPurpose": "",
              "llmRepresentsPersistentState": false,
              "llmRole": "",
              "llmSideEffectLevel": "none",
              "llmTypicalUseCases": [],
              "name": "TestFeature",
              "nodeClickBehavior": "none",
              "nodeCommandBehavior": "",
              $requiresServerLine
              "shortDescription": "",
              "state": "",
              "subcategory": "",
              "title": ""
            }
        """.trimIndent()
    }
}
