package krill.zone.screenshot

import kotlinx.serialization.builtins.ListSerializer
import krill.zone.shared.KrillApp
import krill.zone.shared.Platform
import krill.zone.shared.fastJson
import krill.zone.shared.krillapp.client.ClientMetaData
import krill.zone.shared.krillapp.datapoint.DataPointMetaData
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.krillapp.datapoint.filter.FilterMetaData
import krill.zone.shared.krillapp.executor.calculation.CalculationEngineNodeMetaData
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.krillapp.executor.ExecuteMetaData
import krill.zone.shared.krillapp.project.diagram.DiagramMetaData
import krill.zone.shared.krillapp.server.ServerMetaData
import krill.zone.shared.krillapp.trigger.TriggerMetaData
import krill.zone.shared.node.Node
import krill.zone.shared.node.NodeState

/**
 * Test fixtures for screenshot scenarios.
 *
 * v1 strategy: build fixtures in Kotlin against the real data classes so the
 * serializer contract is exercised at seed time, not at JSON-parse time. We
 * still expose a `loadNodes(name)` JSON entry point so that JSON fixtures can
 * be added under `src/desktopTest/resources/fixtures/` without touching
 * scenario code — but until the first recorded screenshot run produces a
 * known-good JSON dump, authoring those files by hand is error-prone.
 *
 * Follow-up (after first successful record run): dump each Kotlin fixture to
 * `src/desktopTest/resources/fixtures/<name>.json` via `fastJson.encodeToString`
 * and replace the `error(...)` fallback below with classpath loading.
 */
object Fixtures {

    const val CLIENT_ID = "client-fixture"
    const val SERVER_ID = "server-fixture"
    private const val TRIGGER_ID = "trigger-fixture"
    private const val FILTER_ID = "filter-fixture"
    private const val DATAPOINT_ID = "datapoint-fixture"
    private const val EXECUTOR_ID = "executor-fixture"
    private const val DIAGRAM_ID = "diagram-fixture"

    /** A fully-populated swarm with one node of each major category. */
    fun populatedSwarm(): List<Node> {
        val client = Node(
            id = CLIENT_ID,
            parent = CLIENT_ID,
            host = CLIENT_ID,
            type = KrillApp.Client,
            state = NodeState.EXECUTED,
            meta = ClientMetaData(name = "Demo Client"),
        )
        val server = Node(
            id = SERVER_ID,
            parent = CLIENT_ID,
            host = SERVER_ID,
            type = KrillApp.Server,
            state = NodeState.EXECUTED,
            meta = ServerMetaData(
                name = "pi-krill",
                port = 50051,
                model = "Raspberry Pi 4",
                version = "1.0.876",
                os = "Debian 12",
                platform = Platform.DESKTOP,
                isLocal = true,
            ),
        )
        val datapoint = Node(
            id = DATAPOINT_ID,
            parent = SERVER_ID,
            host = SERVER_ID,
            type = KrillApp.DataPoint,
            state = NodeState.EXECUTED,
            meta = DataPointMetaData(
                name = "Kitchen Temperature",
                snapshot = Snapshot(timestamp = 0L, value = 24.5),
                unit = "°C",
            ),
        )
        val trigger = Node(
            id = TRIGGER_ID,
            parent = DATAPOINT_ID,
            host = SERVER_ID,
            type = KrillApp.Trigger.HighThreshold,
            state = NodeState.EXECUTED,
            meta = TriggerMetaData(name = "Hot", value = 28.0),
        )
        val filter = Node(
            id = FILTER_ID,
            parent = DATAPOINT_ID,
            host = SERVER_ID,
            type = KrillApp.DataPoint.Filter.Deadband,
            state = NodeState.EXECUTED,
            meta = FilterMetaData(name = "Deadband"),
        )
        val executor = Node(
            id = EXECUTOR_ID,
            parent = TRIGGER_ID,
            host = SERVER_ID,
            type = KrillApp.Executor.OutgoingWebHook,
            state = NodeState.EXECUTED,
            meta = ExecuteMetaData(name = "Alert webhook"),
        )
        val diagram = Node(
            id = DIAGRAM_ID,
            parent = SERVER_ID,
            host = SERVER_ID,
            type = KrillApp.Project.Diagram,
            state = NodeState.EXECUTED,
            meta = DiagramMetaData(name = "Kitchen"),
        )
        return listOf(client, server, datapoint, trigger, filter, executor, diagram)
    }

    // ---- Calculation editor scenario --------------------------------------

    /** ID of the target `counter` data point for the calculation scenario. */
    const val COUNTER_ID = "counter"

    /** ID of the calculation node referencing `counter` in `counter = counter + 1`. */
    const val CALCULATION_ID = "calc-counter-plus-one"

    /**
     * Scenario swarm for the `editor__calculation-counter` screenshot.
     *
     * Builds a single `counter` [KrillApp.DataPoint] and a
     * [KrillApp.Executor.Calculation] whose formula is `[<host>:counter] + 1`,
     * with both sources and targets pointing at the counter. This models the
     * idiomatic "self-incrementing" calculation used in the calculation
     * executor blog post.
     */
    fun calculationCounterSwarm(): List<Node> {
        val client = Node(
            id = CLIENT_ID,
            parent = CLIENT_ID,
            host = CLIENT_ID,
            type = KrillApp.Client,
            state = NodeState.EXECUTED,
            meta = ClientMetaData(name = "Demo Client"),
        )
        val server = Node(
            id = SERVER_ID,
            parent = CLIENT_ID,
            host = SERVER_ID,
            type = KrillApp.Server,
            state = NodeState.EXECUTED,
            meta = ServerMetaData(
                name = "pi-krill",
                port = 50051,
                version = "1.0.876",
                platform = Platform.DESKTOP,
                isLocal = true,
            ),
        )
        val counter = Node(
            id = COUNTER_ID,
            parent = SERVER_ID,
            host = SERVER_ID,
            type = KrillApp.DataPoint,
            state = NodeState.EXECUTED,
            meta = DataPointMetaData(
                name = "counter",
                snapshot = Snapshot(timestamp = 0L, value = 41.0),
            ),
        )
        val counterIdentity = NodeIdentity(nodeId = COUNTER_ID, hostId = SERVER_ID)
        val calculation = Node(
            id = CALCULATION_ID,
            parent = COUNTER_ID,
            host = SERVER_ID,
            type = KrillApp.Executor.Calculation,
            state = NodeState.EXECUTED,
            meta = CalculationEngineNodeMetaData(
                sources = listOf(counterIdentity),
                targets = listOf(counterIdentity),
                formula = "[$SERVER_ID:$COUNTER_ID]+1",
                executionSource = listOf(ExecutionSource.SOURCE_VALUE_MODIFIED),
            ),
        )
        return listOf(client, server, counter, calculation)
    }

    /** Just the client node (empty-swarm scenario). */
    fun emptySwarm(): List<Node> = listOf(
        Node(
            id = CLIENT_ID,
            parent = CLIENT_ID,
            host = CLIENT_ID,
            type = KrillApp.Client,
            state = NodeState.EXECUTED,
            meta = ClientMetaData(name = "Demo Client"),
        )
    )

    /**
     * Attempt to load a named JSON fixture from the classpath. Returns `null`
     * if not present — scenarios fall back to Kotlin fixtures.
     *
     * Kept for forward-compat: the plan is to generate these JSON files as a
     * one-shot after the harness's first successful record run.
     */
    fun loadNodesFromResources(fixtureName: String): List<Node>? {
        val stream = javaClass.classLoader.getResourceAsStream("fixtures/$fixtureName.json")
            ?: return null
        val json = stream.bufferedReader().use { it.readText() }
        return fastJson.decodeFromString(ListSerializer(Node.serializer()), json)
    }
}
