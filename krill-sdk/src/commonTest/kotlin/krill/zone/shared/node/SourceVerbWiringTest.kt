package krill.zone.shared.node

import kotlinx.serialization.json.Json
import krill.zone.shared.events.EventPayload
import krill.zone.shared.events.EventType
import krill.zone.shared.events.SourceTriggerPayload
import krill.zone.shared.krillapp.client.ClientMetaData
import krill.zone.shared.krillapp.datapoint.DataPointMetaData
import krill.zone.shared.krillapp.datapoint.filter.FilterMetaData
import krill.zone.shared.krillapp.executor.ExecuteMetaData
import krill.zone.shared.krillapp.project.ProjectMetaData
import krill.zone.shared.krillapp.project.camera.CameraMetaData
import krill.zone.shared.krillapp.project.diagram.DiagramMetaData
import krill.zone.shared.krillapp.project.journal.JournalMetaData
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.server.backup.BackupMetaData
import krill.zone.shared.krillapp.server.llm.LLMMetaData
import krill.zone.shared.krillapp.server.pin.PinMetaData
import krill.zone.shared.krillapp.server.serialdevice.SerialDeviceTargetMetaData
import krill.zone.shared.krillapp.spacer.SpacerMetaData
import krill.zone.shared.krillapp.trigger.TriggerMetaData
import krill.zone.shared.krillapp.trigger.button.ButtonMetaData
import krill.zone.shared.krillapp.trigger.color.ColorTriggerMetaData
import krill.zone.shared.krillapp.trigger.cron.CronMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract tests for unify-source-verb-wiring, Part 1 (krill-sdk).
 *
 * Covers:
 *  - the dispatch contract that carries the originating [NodeIdentity] +
 *    its verb to a woken receiver ([SourceTriggerPayload]);
 *  - universal [TargetingNodeMetaData] across every shipped MetaData type;
 *  - back-compat: payloads predating this change deserialise with safe
 *    defaults (empty wiring, [NodeAction.EXECUTE]).
 */
class SourceVerbWiringTest {

    // ---- D4: dispatch contract carries the originating identity + verb ----

    @Test
    fun `SourceTriggerPayload carries the originating identity and verb`() {
        val origin = NodeIdentity(nodeId = "btn-1", hostId = "host-a")
        val payload = SourceTriggerPayload(triggeringSource = origin, nodeAction = NodeAction.RESET)

        assertEquals(origin, payload.triggeringSource)
        assertEquals(NodeAction.RESET, payload.nodeAction)
    }

    @Test
    fun `SourceTriggerPayload is an EventPayload with a dedicated EventType`() {
        // The SOURCE_TRIGGERED EventType + EventPayload subtype are the SDK
        // contract; the polymorphic registration that lets it ride an Event
        // envelope is the consuming module's project-wide serializer (Part 2).
        val payload: EventPayload = SourceTriggerPayload(
            NodeIdentity("pin-7", "host-b"), NodeAction.RESET,
        )
        assertTrue(payload is SourceTriggerPayload)
        assertEquals("SOURCE_TRIGGERED", EventType.SOURCE_TRIGGERED.name)
    }

    @Test
    fun `SourceTriggerPayload round-trips on its own serializer`() {
        val origin = NodeIdentity(nodeId = "pin-7", hostId = "host-b")
        val payload = SourceTriggerPayload(origin, NodeAction.RESET)
        val decoded = Json.decodeFromString(
            SourceTriggerPayload.serializer(),
            Json.encodeToString(SourceTriggerPayload.serializer(), payload),
        )
        assertEquals(origin, decoded.triggeringSource)
        assertEquals(NodeAction.RESET, decoded.nodeAction)
    }

    @Test
    fun `SourceTriggerPayload defaults to EXECUTE when verb absent in payload`() {
        val json = """{"triggeringSource":{"nodeId":"n","hostId":"h"}}"""
        val decoded = Json.decodeFromString(SourceTriggerPayload.serializer(), json)
        assertEquals(NodeAction.EXECUTE, decoded.nodeAction)
    }

    // ---- D3: every shipped MetaData type is a TargetingNodeMetaData ----

    /** One pre-change payload per newly-targeting type (absent wiring + verb). */
    private val preChangePayloads: Map<String, TargetingNodeMetaData> = mapOf(
        "ClientMetaData" to dec(ClientMetaData.serializer(), """{"name":"ui"}"""),
        "DataPointMetaData" to dec(DataPointMetaData.serializer(), """{"name":"dp"}"""),
        "DiagramMetaData" to dec(DiagramMetaData.serializer(), """{"name":"d"}"""),
        "ExecuteMetaData" to dec(ExecuteMetaData.serializer(), """{"name":"ex"}"""),
        "LLMMetaData" to dec(LLMMetaData.serializer(), """{"model":"m"}"""),
        "ProjectMetaData" to dec(ProjectMetaData.serializer(), """{"name":"p"}"""),
        "FilterMetaData" to dec(FilterMetaData.serializer(), """{"name":"f","value":1.0}"""),
        "SerialDeviceTargetMetaData" to dec(SerialDeviceTargetMetaData.serializer(), """{"name":"t"}"""),
        "BackupMetaData" to dec(BackupMetaData.serializer(), """{"name":"b"}"""),
        "SpacerMetaData" to dec(SpacerMetaData.serializer(), """{"name":"s"}"""),
        "CameraMetaData" to dec(CameraMetaData.serializer(), """{"name":"c"}"""),
        "JournalMetaData" to dec(JournalMetaData.serializer(), """{"name":"j"}"""),
        "ServerMetaData" to dec(ServerMetaData.serializer(), """{"name":"srv"}"""),
        "PinMetaData" to dec(PinMetaData.serializer(), """{"name":"pin"}"""),
        "ButtonMetaData" to dec(ButtonMetaData.serializer(), """{"name":"btn"}"""),
        "ColorTriggerMetaData" to dec(ColorTriggerMetaData.serializer(), """{"name":"col"}"""),
        "TriggerMetaData" to dec(TriggerMetaData.serializer(), """{"name":"HighThreshold","value":42.0}"""),
        "CronMetaData" to dec(CronMetaData.serializer(), """{"name":"cron"}"""),
    )

    @Test
    fun `every shipped MetaData type implements TargetingNodeMetaData`() {
        // The contract is enforced at compile time: `preChangePayloads` is a
        // Map<String, TargetingNodeMetaData>, so every `dec(X.serializer(), …)`
        // entry only compiles if X implements TargetingNodeMetaData. This test
        // guards that the audited set stays populated (and is the place to add
        // a new shipped type's entry).
        assertEquals(18, preChangePayloads.size)
    }

    @Test
    fun `pre-change payloads deserialise with safe wiring defaults`() {
        for ((name, meta) in preChangePayloads) {
            assertTrue(meta.sources.isEmpty(), "$name.sources should default empty")
            assertTrue(meta.targets.isEmpty(), "$name.targets should default empty")
            assertTrue(meta.executionSource.isEmpty(), "$name.executionSource should default empty")
            assertEquals(NodeAction.EXECUTE, meta.nodeAction, "$name.nodeAction should default EXECUTE")
        }
    }

    @Test
    fun `populated wiring round-trips on a newly-targeting type`() {
        val src = NodeIdentity(nodeId = "btn-1", hostId = "host-a")
        val meta = PinMetaData(
            name = "relay",
            sources = listOf(src),
            targets = listOf(NodeIdentity("dp-2", "host-a")),
            executionSource = listOf(ExecutionSource.SOURCE_VALUE_MODIFIED),
            nodeAction = NodeAction.RESET,
        )
        val decoded = Json.decodeFromString(
            PinMetaData.serializer(),
            Json.encodeToString(PinMetaData.serializer(), meta),
        )
        assertEquals(listOf(src), decoded.sources)
        assertEquals(listOf(ExecutionSource.SOURCE_VALUE_MODIFIED), decoded.executionSource)
        assertEquals(NodeAction.RESET, decoded.nodeAction)
    }

    private fun <T> dec(serializer: kotlinx.serialization.KSerializer<T>, json: String): T =
        Json.decodeFromString(serializer, json)
}
