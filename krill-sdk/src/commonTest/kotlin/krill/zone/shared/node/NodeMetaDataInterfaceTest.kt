package krill.zone.shared.node

import krill.zone.shared.KrillApp
import krill.zone.shared.krillapp.datapoint.DataPointMetaData
import krill.zone.shared.krillapp.datapoint.DataType
import krill.zone.shared.krillapp.datapoint.graph.GraphMetaData
import krill.zone.shared.krillapp.executor.logicgate.LogicGate
import krill.zone.shared.krillapp.executor.logicgate.LogicGateMetaData
import krill.zone.shared.krillapp.project.tasklist.TaskListMetaData
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.server.backup.BackupMetaData
import krill.zone.shared.krillapp.server.llm.LLMMetaData
import krill.zone.shared.krillapp.server.pin.PinMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression for krill-oss#146: NodeMetaData.withError / displayName / isDigital
 * interface methods replace exhaustive when-dispatch in NodeMetaDataUpdate and
 * NodeFunctions, eliminating silent-failure on unknown subtypes.
 */
class NodeMetaDataInterfaceTest {

    // ── withError ─────────────────────────────────────────────────────────────

    @Test
    fun `withError preserves all fields and sets error on ServerMetaData`() {
        val meta = ServerMetaData(name = "pi.local", port = 8443, error = "")
        val updated = meta.withError("network timeout")
        assertEquals("network timeout", updated.error)
        assertEquals("pi.local", updated.name)
        assertEquals(8443, updated.port)
    }

    @Test
    fun `withError preserves all fields and sets error on PinMetaData`() {
        val meta = PinMetaData(name = "relay", pinNumber = 12, error = "")
        val updated = meta.withError("gpio error")
        assertEquals("gpio error", updated.error)
        assertEquals("relay", updated.name)
        assertEquals(12, updated.pinNumber)
    }

    @Test
    fun `withError works on LLMMetaData - was silently missing from exhaustive when dispatch`() {
        val meta = LLMMetaData(model = "qwen", error = "")
        val updated = meta.withError("inference failed")
        assertEquals("inference failed", updated.error)
        assertEquals("qwen", updated.model)
    }

    @Test
    fun `updateMetaWithError delegates to withError correctly`() {
        val meta = BackupMetaData(name = "nightly", error = "")
        val updated = updateMetaWithError(meta, "disk full") as BackupMetaData
        assertEquals("disk full", updated.error)
        assertEquals("nightly", updated.name)
    }

    @Test
    fun `updateMetaWithError clears error when given empty string`() {
        val meta = PinMetaData(name = "relay", error = "prior error")
        val cleared = updateMetaWithError(meta, "") as PinMetaData
        assertEquals("", cleared.error)
    }

    // ── displayName ───────────────────────────────────────────────────────────

    @Test
    fun `displayName returns name field for ServerMetaData`() {
        assertEquals("my-pi", ServerMetaData(name = "my-pi").displayName())
    }

    @Test
    fun `displayName returns name field for PinMetaData`() {
        assertEquals("relay", PinMetaData(name = "relay").displayName())
    }

    @Test
    fun `displayName falls back to Backup when BackupMetaData name is empty`() {
        assertEquals("Backup", BackupMetaData(name = "").displayName())
        assertEquals("nightly", BackupMetaData(name = "nightly").displayName())
    }

    @Test
    fun `displayName falls back to Graph when GraphMetaData name is empty`() {
        assertEquals("Graph", GraphMetaData(name = "").displayName())
        assertEquals("temp graph", GraphMetaData(name = "temp graph").displayName())
    }

    @Test
    fun `displayName on LogicGateMetaData falls back to gate type when name is empty`() {
        val gateWithName = LogicGateMetaData(name = "my gate", gateType = LogicGate.AND)
        assertEquals("my gate", gateWithName.displayName())

        val gateNoName = LogicGateMetaData(name = "", gateType = LogicGate.AND)
        assertEquals("AND", gateNoName.displayName())
    }

    @Test
    fun `displayName returns empty for types with no dedicated name field such as LLMMetaData`() {
        assertEquals("", LLMMetaData().displayName())
    }

    // ── Node.name() end-to-end ────────────────────────────────────────────────

    private fun nodeOf(type: KrillApp, meta: NodeMetaData) = Node(
        id = "n1", parent = "s1", host = "s1", type = type, meta = meta,
    )

    @Test
    fun `Node name returns meta displayName when non-empty`() {
        val node = nodeOf(KrillApp.Server, ServerMetaData(name = "garage.local"))
        assertEquals("garage.local", node.name())
    }

    @Test
    fun `Node name falls back to type string when displayName is empty`() {
        val node = nodeOf(KrillApp.Server.LLM, LLMMetaData())
        assertEquals(KrillApp.Server.LLM.toString(), node.name())
    }

    // ── isDigital ─────────────────────────────────────────────────────────────

    @Test
    fun `PinMetaData isDigital returns true`() {
        assertTrue(PinMetaData().isDigital())
    }

    @Test
    fun `LogicGateMetaData isDigital returns true`() {
        assertTrue(LogicGateMetaData().isDigital())
    }

    @Test
    fun `TaskListMetaData isDigital returns true`() {
        assertTrue(TaskListMetaData().isDigital())
    }

    @Test
    fun `DataPointMetaData isDigital returns true only for DIGITAL dataType`() {
        assertTrue(DataPointMetaData(dataType = DataType.DIGITAL).isDigital())
        assertFalse(DataPointMetaData(dataType = DataType.DOUBLE).isDigital())
        assertFalse(DataPointMetaData(dataType = DataType.COLOR).isDigital())
    }

    @Test
    fun `ServerMetaData isDigital returns false`() {
        assertFalse(ServerMetaData().isDigital())
    }

    @Test
    fun `LLMMetaData isDigital returns false`() {
        assertFalse(LLMMetaData().isDigital())
    }

    // ── Node.isDigital() end-to-end ───────────────────────────────────────────

    @Test
    fun `Node isDigital delegates correctly to metadata`() {
        assertTrue(nodeOf(KrillApp.Server.Pin, PinMetaData()).isDigital())
        assertTrue(nodeOf(KrillApp.Executor.LogicGate, LogicGateMetaData()).isDigital())
        assertTrue(nodeOf(KrillApp.Project.TaskList, TaskListMetaData()).isDigital())
        assertTrue(nodeOf(KrillApp.DataPoint, DataPointMetaData(dataType = DataType.DIGITAL)).isDigital())
        assertFalse(nodeOf(KrillApp.DataPoint, DataPointMetaData(dataType = DataType.DOUBLE)).isDigital())
        assertFalse(nodeOf(KrillApp.Server, ServerMetaData()).isDigital())
    }
}
