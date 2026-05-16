package krill.zone.shared.node

import kotlinx.serialization.json.Json
import krill.zone.shared.krillapp.trigger.button.ButtonMetaData
import krill.zone.shared.krillapp.trigger.TriggerMetaData
import krill.zone.shared.krillapp.executor.smtp.SMTPMetaData
import krill.zone.shared.krillapp.executor.logicgate.LogicGateMetaData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for Sautner-Studio-LLC/krill-oss#80 — NodeAction back-compat.
 *
 * Payloads persisted before 0.0.23 lack the `nodeAction` field. Every
 * concrete implementation defaults to [NodeAction.EXECUTE]; deserialising
 * a pre-field payload must yield that default, not a serialisation error.
 */
class NodeActionTest {

    @Test
    fun `ButtonMetaData without nodeAction field deserialises as EXECUTE`() {
        val json = """{"name":"button","error":""}"""
        val meta = Json.decodeFromString(ButtonMetaData.serializer(), json)
        assertEquals(NodeAction.EXECUTE, meta.nodeAction)
    }

    @Test
    fun `TriggerMetaData without nodeAction field deserialises as EXECUTE`() {
        val json = """{"name":"HighThreshold","value":42.0,"error":""}"""
        val meta = Json.decodeFromString(TriggerMetaData.serializer(), json)
        assertEquals(NodeAction.EXECUTE, meta.nodeAction)
    }

    @Test
    fun `SMTPMetaData without nodeAction field deserialises as EXECUTE`() {
        val json = """{"host":"smtp.example.com","port":587,"username":"","token":"","fromAddress":"","toAddress":"","sources":[],"targets":[],"executionSource":[],"error":""}"""
        val meta = Json.decodeFromString(SMTPMetaData.serializer(), json)
        assertEquals(NodeAction.EXECUTE, meta.nodeAction)
    }

    @Test
    fun `LogicGateMetaData without nodeAction field deserialises as EXECUTE`() {
        val json = """{"name":"logic gate","gateType":"BUFFER","sources":[{"nodeId":"","hostId":""}],"targets":[],"executionSource":[],"error":""}"""
        val meta = Json.decodeFromString(LogicGateMetaData.serializer(), json)
        assertEquals(NodeAction.EXECUTE, meta.nodeAction)
    }

    @Test
    fun `NodeAction RESET round-trips`() {
        val meta = ButtonMetaData(name = "reset-btn", nodeAction = NodeAction.RESET)
        val json = Json.encodeToString(ButtonMetaData.serializer(), meta)
        val decoded = Json.decodeFromString(ButtonMetaData.serializer(), json)
        assertEquals(NodeAction.RESET, decoded.nodeAction)
    }
}
