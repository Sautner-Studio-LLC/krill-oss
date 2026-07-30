package krill.zone.shared.node

import kotlinx.serialization.json.Json
import krill.zone.shared.krillapp.server.llm.LLMMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * Regression tests for krill-oss#217: [NodeAction] gains ADVERTISE and CLAIM.
 *
 * Covers:
 *  - Both new verbs round-trip on their own serializer.
 *  - A payload carrying one of the new verbs deserializes correctly on an
 *    existing [SourceMetaData] implementation ([LLMMetaData]).
 *  - A payload naming an unknown verb fails to decode rather than silently
 *    defaulting — the SDK's contribution to "degrade to refusal, never
 *    crash" is a loud decode error a caller can catch, not a wrong verb.
 */
class NodeActionSwarmVerbsTest {

    @Test
    fun `ADVERTISE and CLAIM round-trip on NodeAction's own serializer`() {
        for (action in listOf(NodeAction.ADVERTISE, NodeAction.CLAIM)) {
            val encoded = Json.encodeToString(NodeAction.serializer(), action)
            val decoded = Json.decodeFromString(NodeAction.serializer(), encoded)
            assertEquals(action, decoded)
        }
    }

    @Test
    fun `LLMMetaData round-trips with nodeAction ADVERTISE`() {
        val meta = LLMMetaData(model = "qwen", nodeAction = NodeAction.ADVERTISE)
        val encoded = Json.encodeToString(LLMMetaData.serializer(), meta)
        val decoded = Json.decodeFromString(LLMMetaData.serializer(), encoded)
        assertEquals(NodeAction.ADVERTISE, decoded.nodeAction)
    }

    @Test
    fun `LLMMetaData round-trips with nodeAction CLAIM`() {
        val meta = LLMMetaData(model = "qwen", nodeAction = NodeAction.CLAIM)
        val encoded = Json.encodeToString(LLMMetaData.serializer(), meta)
        val decoded = Json.decodeFromString(LLMMetaData.serializer(), encoded)
        assertEquals(NodeAction.CLAIM, decoded.nodeAction)
    }

    @Test
    fun `an unrecognised nodeAction verb fails to decode rather than silently defaulting`() {
        val payload = """{"model":"m","nodeAction":"SOME_FUTURE_VERB"}"""
        assertFails { Json.decodeFromString(LLMMetaData.serializer(), payload) }
    }
}
