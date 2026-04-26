package krill.zone.mcp.krill

import kotlinx.serialization.json.*

/**
 * Static registry of every createable KrillApp node type.
 *
 * Each entry carries the two polymorphic discriminator strings the Krill server
 * expects on the wire, a default `meta` skeleton, and parent/child hints lifted
 * from the real `krill` repo's `KrillApp.kt` + `Serializer.kt`.
 *
 * Mapping rules:
 *   - `typeFqn`  — fully qualified Kotlin class name of the `KrillApp.*` data
 *     object, which is what kotlinx.serialization emits as the `type.type`
 *     discriminator (no `@SerialName` overrides exist).
 *   - `metaFqn`  — FQN of the MetaData class used for that type (looked up
 *     from the `meta = { ... }` lambdas in KrillApp.kt). Several KrillApp
 *     types share a MetaData class (all Triggers → TriggerMetaData; all
 *     Filters → FilterMetaData).
 *   - `defaultMeta` — minimal valid JsonObject for that meta class. All meta
 *     classes tolerate `ignoreUnknownKeys=true`, so callers can freely overlay
 *     extra keys; fields omitted here fall back to the Kotlin data class
 *     defaults on the server.
 *
 * When the upstream `krill` repo adds a new KrillApp subtype, add an entry
 * here and bump the mcp/skill version.
 */
data class KrillNodeType(
    val shortName: String,
    val typeFqn: String,
    val metaFqn: String,
    val defaultMeta: JsonObject,
    val role: String,
    val sideEffect: String,
    val description: String,
    val validParentTypes: List<String>,
    val validChildTypes: List<String>,
    val notes: String? = null,
    /**
     * Field-shape disambiguation for meta fields whose JSON type alone doesn't
     * convey enough — empty arrays (`sources: []`) don't show their element
     * shape, enum fields look like bare strings. Keyed by meta field name.
     * Only populated for fields where the default doesn't already make the
     * shape obvious.
     */
    val metaFieldHints: Map<String, String> = emptyMap(),
)

private val NODE_IDENTITY_HINT =
    "List<{nodeId: String, hostId: String}> — NodeIdentity. hostId is the server UUID that owns the referenced node."

object KrillNodeTypes {

    /** `meta.type` polymorphic discriminator key — kotlinx.serialization default. */
    const val META_TYPE_KEY = "type"

    private val defaultNodeMetaFields: Map<String, JsonElement> = mapOf("error" to JsonPrimitive(""))

    private fun meta(fqn: String, vararg extra: Pair<String, JsonElement>): JsonObject = buildJsonObject {
        put("type", fqn)
        defaultNodeMetaFields.forEach { (k, v) -> put(k, v) }
        extra.forEach { (k, v) -> put(k, v) }
    }

    /**
     * Empty Snapshot skeleton. value is always a string on the wire — server
     * parses it per the DataPoint's `dataType`.
     */
    private fun snapshotDefault(): JsonObject = buildJsonObject {
        put("timestamp", 0L)
        put("value", "0.0")
    }

    private fun nodeIdentityArray(vararg identities: Pair<String, String>): JsonArray = JsonArray(
        identities.map { (nodeId, hostId) ->
            buildJsonObject {
                put("nodeId", nodeId)
                put("hostId", hostId)
            }
        }
    )

    private val TYPE_TABLE: List<KrillNodeType> = listOf(
        // ── Project container tree ─────────────────────────────────────────
        KrillNodeType(
            shortName = "KrillApp.Project",
            typeFqn = "krill.zone.shared.KrillApp.Project",
            metaFqn = "krill.zone.shared.krillapp.project.ProjectMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.project.ProjectMetaData",
                "name" to JsonPrimitive("Project"),
                "description" to JsonPrimitive(""),
            ),
            role = "container",
            sideEffect = "none",
            description = "Organizational container for Diagrams, TaskLists, Journals, Cameras.",
            validParentTypes = listOf("KrillApp.Server"),
            validChildTypes = listOf(
                "KrillApp.Project.Diagram",
                "KrillApp.Project.TaskList",
                "KrillApp.Project.Journal",
                "KrillApp.Project.Camera",
            ),
            notes = "Prefer the `create_project` tool — it sets the same fields and is self-documenting.",
        ),
        KrillNodeType(
            shortName = "KrillApp.Project.Diagram",
            typeFqn = "krill.zone.shared.KrillApp.Project.Diagram",
            metaFqn = "krill.zone.shared.krillapp.project.diagram.DiagramMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.project.diagram.DiagramMetaData",
                "name" to JsonPrimitive("Diagram"),
                "description" to JsonPrimitive(""),
                "source" to JsonPrimitive(""),
                "anchorBindings" to JsonObject(emptyMap()),
            ),
            role = "display",
            sideEffect = "none",
            description = "SVG diagram with anchored nodes. Use the `create_diagram` tool — it uploads the SVG file and computes meta.source.",
            validParentTypes = listOf("KrillApp.Project"),
            validChildTypes = emptyList(),
            notes = "Do not create via `create_node` — use `create_diagram` to get the SVG upload + URL round-trip.",
        ),
        KrillNodeType(
            shortName = "KrillApp.Project.TaskList",
            typeFqn = "krill.zone.shared.KrillApp.Project.TaskList",
            metaFqn = "krill.zone.shared.krillapp.project.tasklist.TaskListMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.project.tasklist.TaskListMetaData",
                "name" to JsonPrimitive("Task List"),
                "description" to JsonPrimitive(""),
                "tasks" to JsonArray(emptyList()),
                "priority" to JsonPrimitive("NONE"),
                "createdAt" to JsonPrimitive(0L),
                "updatedAt" to JsonPrimitive(0L),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Stateful checklist that fires child executors when tasks become overdue.",
            validParentTypes = listOf("KrillApp.Project"),
            validChildTypes = listOf("KrillApp.Executor"),
            notes = "`priority` ∈ {NONE, LOW, MEDIUM, HIGH}. `tasks[]` entries carry their own cron/dueAt fields — overlay via the `meta` argument.",
            metaFieldHints = mapOf(
                "priority" to "enum: NONE | LOW | MEDIUM | HIGH",
                "tasks" to "List<Task> — each entry is the shape Krill's TaskList stores; consult the Krill source or an existing TaskList node via `get_node` for the full Task shape before authoring.",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Project.Journal",
            typeFqn = "krill.zone.shared.KrillApp.Project.Journal",
            metaFqn = "krill.zone.shared.krillapp.project.journal.JournalMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.project.journal.JournalMetaData",
                "name" to JsonPrimitive("Journal"),
                "description" to JsonPrimitive(""),
                "entries" to JsonArray(emptyList()),
                "createdAt" to JsonPrimitive(0L),
                "updatedAt" to JsonPrimitive(0L),
            ),
            role = "state",
            sideEffect = "none",
            description = "Chronological journal for project progress and observations.",
            validParentTypes = listOf("KrillApp.Project"),
            validChildTypes = emptyList(),
        ),
        KrillNodeType(
            shortName = "KrillApp.Project.Camera",
            typeFqn = "krill.zone.shared.KrillApp.Project.Camera",
            metaFqn = "krill.zone.shared.krillapp.project.camera.CameraMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.project.camera.CameraMetaData",
                "name" to JsonPrimitive(""),
                "resolution" to JsonPrimitive("1280x720"),
                "framerate" to JsonPrimitive(15),
                "rotation" to JsonPrimitive(0),
                "streamPort" to JsonPrimitive(8443),
                "enabled" to JsonPrimitive(true),
            ),
            role = "sensor",
            sideEffect = "low",
            description = "Live camera feed from a Pi camera module or USB camera.",
            validParentTypes = listOf("KrillApp.Project"),
            validChildTypes = emptyList(),
        ),

        // ── DataPoint ──────────────────────────────────────────────────────
        KrillNodeType(
            shortName = "KrillApp.DataPoint",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint",
            metaFqn = "krill.zone.shared.krillapp.datapoint.DataPointMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.DataPointMetaData",
                "name" to JsonPrimitive("data-point"),
                "sourceId" to JsonPrimitive(""),
                "snapshot" to snapshotDefault(),
                "precision" to JsonPrimitive(2),
                "unit" to JsonPrimitive(""),
                "manualEntry" to JsonPrimitive(true),
                "dataType" to JsonPrimitive("DOUBLE"),
                "maxAge" to JsonPrimitive(0L),
                "path" to JsonPrimitive(""),
            ),
            role = "state",
            sideEffect = "low",
            description = "Stores time-series snapshot values and triggers downstream nodes on data updates.",
            validParentTypes = listOf("KrillApp.Server", "KrillApp.Server.SerialDevice"),
            validChildTypes = listOf(
                "KrillApp.DataPoint.Filter",
                "KrillApp.DataPoint.Graph",
                "KrillApp.Trigger",
                "KrillApp.Trigger.HighThreshold",
                "KrillApp.Trigger.LowThreshold",
                "KrillApp.Trigger.CronTimer",
                "KrillApp.Trigger.Button",
                "KrillApp.Trigger.SilentAlarmMs",
                "KrillApp.Executor",
            ),
            notes = "`dataType` ∈ {TEXT, JSON, DIGITAL, DOUBLE, COLOR}. Use `record_snapshot` to write values.",
            metaFieldHints = mapOf(
                "dataType" to "enum: TEXT | JSON | DIGITAL | DOUBLE | COLOR",
                "snapshot" to "{timestamp: Long (epoch ms), value: String} — value format depends on dataType; see `record_snapshot` docs.",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.DataPoint.Filter",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint.Filter",
            metaFqn = "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
                "name" to JsonPrimitive("Filter"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "container",
            sideEffect = "none",
            description = "Container for filter nodes that validate incoming DataPoint snapshots.",
            validParentTypes = listOf("KrillApp.DataPoint"),
            validChildTypes = listOf(
                "KrillApp.DataPoint.Filter.DiscardAbove",
                "KrillApp.DataPoint.Filter.DiscardBelow",
                "KrillApp.DataPoint.Filter.Deadband",
                "KrillApp.DataPoint.Filter.Debounce",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.DataPoint.Filter.DiscardAbove",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint.Filter.DiscardAbove",
            metaFqn = "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
                "name" to JsonPrimitive("DiscardAbove"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "logic",
            sideEffect = "none",
            description = "Discards snapshots with values above `meta.value`.",
            validParentTypes = listOf("KrillApp.DataPoint.Filter"),
            validChildTypes = emptyList(),
        ),
        KrillNodeType(
            shortName = "KrillApp.DataPoint.Filter.DiscardBelow",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint.Filter.DiscardBelow",
            metaFqn = "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
                "name" to JsonPrimitive("DiscardBelow"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "logic",
            sideEffect = "none",
            description = "Discards snapshots with values below `meta.value`.",
            validParentTypes = listOf("KrillApp.DataPoint.Filter"),
            validChildTypes = emptyList(),
        ),
        KrillNodeType(
            shortName = "KrillApp.DataPoint.Filter.Deadband",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint.Filter.Deadband",
            metaFqn = "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
                "name" to JsonPrimitive("Deadband"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "logic",
            sideEffect = "none",
            description = "Discards snapshots whose |value − previous| is below `meta.value`.",
            validParentTypes = listOf("KrillApp.DataPoint.Filter"),
            validChildTypes = emptyList(),
        ),
        KrillNodeType(
            shortName = "KrillApp.DataPoint.Filter.Debounce",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint.Filter.Debounce",
            metaFqn = "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.filter.FilterMetaData",
                "name" to JsonPrimitive("Debounce"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "logic",
            sideEffect = "none",
            description = "Discards snapshots arriving within `meta.value` ms of the previous one.",
            validParentTypes = listOf("KrillApp.DataPoint.Filter"),
            validChildTypes = emptyList(),
        ),
        KrillNodeType(
            shortName = "KrillApp.DataPoint.Graph",
            typeFqn = "krill.zone.shared.KrillApp.DataPoint.Graph",
            metaFqn = "krill.zone.shared.krillapp.datapoint.graph.GraphMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.datapoint.graph.GraphMetaData",
                "name" to JsonPrimitive(""),
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "timeRange" to JsonPrimitive("HOUR"),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "display",
            sideEffect = "none",
            description = "Renders historical DataPoint values as a graph over `meta.timeRange`.",
            validParentTypes = listOf("KrillApp.DataPoint"),
            validChildTypes = emptyList(),
            notes = "`timeRange` ∈ {NONE, HOUR, DAY, WEEK, MONTH, YEAR}. Add the parent DataPoint to `sources`. " +
                "Default `name` is empty — `create_node` derives `\"<parent DataPoint name> graph\"` " +
                "from the parent when no `name` is supplied, so siblings under different DataPoints don't collide.",
            metaFieldHints = mapOf(
                "sources" to NODE_IDENTITY_HINT + " For Graph, populate with a single entry: the parent DataPoint's id + host.",
                "targets" to NODE_IDENTITY_HINT + " Unused for Graph — leave empty.",
                "timeRange" to "enum: NONE | HOUR | DAY | WEEK | MONTH | YEAR",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),

        // ── Trigger ────────────────────────────────────────────────────────
        KrillNodeType(
            shortName = "KrillApp.Trigger",
            typeFqn = "krill.zone.shared.KrillApp.Trigger",
            metaFqn = "krill.zone.shared.krillapp.trigger.TriggerMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.TriggerMetaData",
                "name" to JsonPrimitive("Trigger"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "container",
            sideEffect = "none",
            description = "Container for trigger nodes that evaluate conditions or events.",
            validParentTypes = listOf("KrillApp.DataPoint"),
            validChildTypes = listOf(
                "KrillApp.Trigger.Button",
                "KrillApp.Trigger.CronTimer",
                "KrillApp.Trigger.SilentAlarmMs",
                "KrillApp.Trigger.HighThreshold",
                "KrillApp.Trigger.LowThreshold",
                "KrillApp.Trigger.IncomingWebHook",
                "KrillApp.Trigger.Color",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.HighThreshold",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.HighThreshold",
            metaFqn = "krill.zone.shared.krillapp.trigger.TriggerMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.TriggerMetaData",
                "name" to JsonPrimitive("HighThreshold"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Fires when the parent DataPoint's value >= `meta.value`.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf(
                "KrillApp.Executor",
                "KrillApp.Executor.LogicGate",
                "KrillApp.Executor.OutgoingWebHook",
                "KrillApp.Executor.Lambda",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.LowThreshold",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.LowThreshold",
            metaFqn = "krill.zone.shared.krillapp.trigger.TriggerMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.TriggerMetaData",
                "name" to JsonPrimitive("LowThreshold"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Fires when the parent DataPoint's value <= `meta.value`.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf(
                "KrillApp.Executor",
                "KrillApp.Executor.LogicGate",
                "KrillApp.Executor.OutgoingWebHook",
                "KrillApp.Executor.Lambda",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.SilentAlarmMs",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.SilentAlarmMs",
            metaFqn = "krill.zone.shared.krillapp.trigger.TriggerMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.TriggerMetaData",
                "name" to JsonPrimitive("SilentAlarm"),
                "value" to JsonPrimitive(0.0),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Fires when no snapshot arrives within `meta.value` ms.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf("KrillApp.Executor"),
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.Button",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.Button",
            metaFqn = "krill.zone.shared.krillapp.trigger.button.ButtonMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.button.ButtonMetaData",
                "name" to JsonPrimitive("button"),
            ),
            role = "trigger",
            sideEffect = "none",
            description = "Executes child nodes on user click.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf("KrillApp.Executor"),
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.CronTimer",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.CronTimer",
            metaFqn = "krill.zone.shared.krillapp.trigger.cron.CronMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.cron.CronMetaData",
                "name" to JsonPrimitive("CronTimer"),
                "timestamp" to JsonPrimitive(0L),
                "expression" to JsonPrimitive(""),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Executes child nodes on a cron schedule.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf("KrillApp.Executor"),
            notes = "`expression` is a 5-field cron string (e.g. `*/5 * * * *`).",
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.IncomingWebHook",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.IncomingWebHook",
            metaFqn = "krill.zone.shared.krillapp.trigger.webhook.IncomingWebHookMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.webhook.IncomingWebHookMetaData",
                "name" to JsonPrimitive(""),
                "path" to JsonPrimitive(""),
                "method" to JsonPrimitive("GET"),
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Executes child nodes when an HTTP request hits `meta.path`.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf("KrillApp.Executor"),
            metaFieldHints = mapOf(
                "method" to "enum: GET | POST | PUT | DELETE | PATCH",
                "sources" to NODE_IDENTITY_HINT,
                "targets" to NODE_IDENTITY_HINT + " DataPoint(s) that receive the incoming request body.",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Trigger.Color",
            typeFqn = "krill.zone.shared.KrillApp.Trigger.Color",
            metaFqn = "krill.zone.shared.krillapp.trigger.color.ColorTriggerMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.trigger.color.ColorTriggerMetaData",
                "name" to JsonPrimitive("Color Trigger"),
                "rMin" to JsonPrimitive(0),
                "rMax" to JsonPrimitive(255),
                "gMin" to JsonPrimitive(0),
                "gMax" to JsonPrimitive(255),
                "bMin" to JsonPrimitive(0),
                "bMax" to JsonPrimitive(255),
            ),
            role = "trigger",
            sideEffect = "low",
            description = "Fires when the parent DataPoint color falls inside the configured RGB range.",
            validParentTypes = listOf("KrillApp.Trigger", "KrillApp.DataPoint"),
            validChildTypes = listOf("KrillApp.Executor"),
        ),

        // ── Executor ───────────────────────────────────────────────────────
        KrillNodeType(
            shortName = "KrillApp.Executor",
            typeFqn = "krill.zone.shared.KrillApp.Executor",
            metaFqn = "krill.zone.shared.krillapp.executor.ExecuteMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.ExecuteMetaData",
                "name" to JsonPrimitive("Executor"),
                "source" to JsonPrimitive(""),
            ),
            role = "container",
            sideEffect = "none",
            description = "Container for executor nodes that perform actions when triggered.",
            validParentTypes = listOf(
                "KrillApp.Trigger",
                "KrillApp.Trigger.HighThreshold",
                "KrillApp.Trigger.LowThreshold",
                "KrillApp.Trigger.SilentAlarmMs",
                "KrillApp.Trigger.Button",
                "KrillApp.Trigger.CronTimer",
                "KrillApp.Trigger.IncomingWebHook",
                "KrillApp.Trigger.Color",
                "KrillApp.Project.TaskList",
                "KrillApp.DataPoint",
            ),
            validChildTypes = listOf(
                "KrillApp.Executor.LogicGate",
                "KrillApp.Executor.OutgoingWebHook",
                "KrillApp.Executor.Lambda",
                "KrillApp.Executor.Calculation",
                "KrillApp.Executor.Compute",
                "KrillApp.Executor.SMTP",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Executor.LogicGate",
            typeFqn = "krill.zone.shared.KrillApp.Executor.LogicGate",
            metaFqn = "krill.zone.shared.krillapp.executor.logicgate.LogicGateMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.logicgate.LogicGateMetaData",
                "name" to JsonPrimitive("logic gate"),
                "gateType" to JsonPrimitive("BUFFER"),
                "sources" to nodeIdentityArray("" to ""),
                "targets" to nodeIdentityArray(),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "logic",
            sideEffect = "medium",
            description = "Applies a boolean gate to source node values and writes to target.",
            validParentTypes = listOf("KrillApp.Executor", "KrillApp.Trigger"),
            validChildTypes = emptyList(),
            notes = "`gateType` ∈ {BUFFER, NOT, AND, OR, NAND, NOR, XOR, XNOR}.",
            metaFieldHints = mapOf(
                "gateType" to "enum: BUFFER | NOT | AND | OR | NAND | NOR | XOR | XNOR",
                "sources" to NODE_IDENTITY_HINT,
                "targets" to NODE_IDENTITY_HINT,
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Executor.OutgoingWebHook",
            typeFqn = "krill.zone.shared.KrillApp.Executor.OutgoingWebHook",
            metaFqn = "krill.zone.shared.krillapp.executor.webhook.WebHookOutMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.webhook.WebHookOutMetaData",
                "name" to JsonPrimitive(""),
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "url" to JsonPrimitive(""),
                "method" to JsonPrimitive("GET"),
                "params" to JsonArray(emptyList()),
                "headers" to JsonArray(emptyList()),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "action",
            sideEffect = "high",
            description = "Sends an HTTP request to an external URL and stores the response in a target DataPoint.",
            validParentTypes = listOf("KrillApp.Executor", "KrillApp.Trigger"),
            validChildTypes = emptyList(),
            metaFieldHints = mapOf(
                "method" to "enum: GET | POST | PUT | DELETE | PATCH",
                "sources" to NODE_IDENTITY_HINT,
                "targets" to NODE_IDENTITY_HINT + " Target DataPoint(s) receive the HTTP response body.",
                "params" to "List<{first: String, second: String}> — query-string pairs.",
                "headers" to "List<{first: String, second: String}> — request-header pairs.",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Executor.Lambda",
            typeFqn = "krill.zone.shared.KrillApp.Executor.Lambda",
            metaFqn = "krill.zone.shared.krillapp.executor.lambda.LambdaSourceMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.lambda.LambdaSourceMetaData",
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "tags" to JsonObject(emptyMap()),
                "filename" to JsonPrimitive(""),
                "timestamp" to JsonPrimitive(0L),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "action",
            sideEffect = "high",
            description = "Runs a sandboxed Python script that reads from source nodes and writes to target nodes.",
            validParentTypes = listOf("KrillApp.Executor", "KrillApp.Trigger"),
            validChildTypes = emptyList(),
            notes = "`filename` identifies the script on the server. LambdaSourceMetaData has no `name` field — any `name` arg is dropped by `ignoreUnknownKeys`.",
            metaFieldHints = mapOf(
                "sources" to NODE_IDENTITY_HINT + " Inputs the Python script reads.",
                "targets" to NODE_IDENTITY_HINT + " DataPoints the script writes to.",
                "tags" to "Map<String, String> — arbitrary key/value pairs the script can read from its context.",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Executor.Calculation",
            typeFqn = "krill.zone.shared.KrillApp.Executor.Calculation",
            metaFqn = "krill.zone.shared.krillapp.executor.calculation.CalculationEngineNodeMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.calculation.CalculationEngineNodeMetaData",
                "name" to JsonPrimitive("Calculation"),
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "formula" to JsonPrimitive(""),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "transform",
            sideEffect = "low",
            description = "Computes a mathematical formula from source DataPoint values and writes the result to a target DataPoint.",
            validParentTypes = listOf("KrillApp.Executor", "KrillApp.Trigger"),
            validChildTypes = emptyList(),
            metaFieldHints = mapOf(
                "sources" to NODE_IDENTITY_HINT + " DataPoints whose values feed `formula`.",
                "targets" to NODE_IDENTITY_HINT + " DataPoint(s) receiving the computed result.",
                "formula" to "Infix math expression referencing source values by index (e.g. `s0 * 1.8 + 32`).",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Executor.Compute",
            typeFqn = "krill.zone.shared.KrillApp.Executor.Compute",
            metaFqn = "krill.zone.shared.krillapp.executor.compute.ComputeMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.compute.ComputeMetaData",
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "range" to JsonPrimitive("NONE"),
                "operation" to JsonPrimitive("AVERAGE"),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "transform",
            sideEffect = "low",
            description = "Computes a statistical summary of a DataPoint's historical values over a time range.",
            validParentTypes = listOf("KrillApp.Executor", "KrillApp.Trigger"),
            validChildTypes = emptyList(),
            notes = "`operation` ∈ {AVERAGE, MIN, MAX, SUM, COUNT, MEDIAN}. `range` ∈ {NONE, HOUR, DAY, WEEK, MONTH, YEAR}.",
            metaFieldHints = mapOf(
                "sources" to NODE_IDENTITY_HINT + " The DataPoint whose history gets aggregated.",
                "targets" to NODE_IDENTITY_HINT + " DataPoint receiving the aggregate result.",
                "range" to "enum: NONE | HOUR | DAY | WEEK | MONTH | YEAR",
                "operation" to "enum: AVERAGE | MIN | MAX | SUM | COUNT | MEDIAN",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Executor.SMTP",
            typeFqn = "krill.zone.shared.KrillApp.Executor.SMTP",
            metaFqn = "krill.zone.shared.krillapp.executor.smtp.SMTPMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.smtp.SMTPMetaData",
                "host" to JsonPrimitive(""),
                "port" to JsonPrimitive(587),
                "username" to JsonPrimitive(""),
                "token" to JsonPrimitive(""),
                "fromAddress" to JsonPrimitive(""),
                "toAddress" to JsonPrimitive(""),
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "action",
            sideEffect = "high",
            description = "Sends an email via SMTP when triggered.",
            validParentTypes = listOf("KrillApp.Executor", "KrillApp.Trigger"),
            validChildTypes = emptyList(),
            metaFieldHints = mapOf(
                "sources" to NODE_IDENTITY_HINT + " DataPoints whose values can be interpolated into the email.",
                "targets" to NODE_IDENTITY_HINT + " Usually empty — SMTP side-effects go to an external mailbox, not a target DataPoint.",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),

        // ── MQTT ───────────────────────────────────────────────────────────
        KrillNodeType(
            shortName = "KrillApp.MQTT",
            typeFqn = "krill.zone.shared.KrillApp.MQTT",
            metaFqn = "krill.zone.shared.krillapp.executor.mqtt.MqttMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.executor.mqtt.MqttMetaData",
                "sources" to nodeIdentityArray(),
                "targets" to nodeIdentityArray(),
                "address" to JsonPrimitive(""),
                "topic" to JsonPrimitive(""),
                "action" to JsonPrimitive("PUB"),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "action",
            sideEffect = "high",
            description = "Publishes to or subscribes from an MQTT broker topic.",
            validParentTypes = listOf("KrillApp.Server"),
            validChildTypes = emptyList(),
            notes = "`action` ∈ {PUB, SUB}. MqttMetaData has no `name` field.",
            metaFieldHints = mapOf(
                "action" to "enum: PUB | SUB",
                "sources" to NODE_IDENTITY_HINT + " For PUB: DataPoints whose values are published to `topic`.",
                "targets" to NODE_IDENTITY_HINT + " For SUB: DataPoints that receive messages from `topic`.",
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),

        // ── Server-level peripherals ───────────────────────────────────────
        KrillNodeType(
            shortName = "KrillApp.Server.Pin",
            typeFqn = "krill.zone.shared.KrillApp.Server.Pin",
            metaFqn = "krill.zone.shared.krillapp.server.pin.PinMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.server.pin.PinMetaData",
                "name" to JsonPrimitive(""),
                "hardwareId" to JsonPrimitive(""),
                "mode" to JsonPrimitive("OUT"),
                "state" to JsonPrimitive("OFF"),
                "initialState" to JsonPrimitive("OFF"),
                "shutdownState" to JsonPrimitive("OFF"),
                "pinNumber" to JsonPrimitive(0),
                "hardwareType" to JsonPrimitive("GROUND"),
                "isConfigurable" to JsonPrimitive(false),
            ),
            role = "target",
            sideEffect = "high",
            description = "Controls a Raspberry Pi GPIO pin with configurable mode and startup/shutdown behavior.",
            validParentTypes = listOf("KrillApp.Server"),
            validChildTypes = emptyList(),
            notes = "`pinNumber` > 0 and a non-empty `hardwareId` are required before Pi4J will register the pin. `mode` ∈ {IN, OUT, PWM}.",
            metaFieldHints = mapOf(
                "mode" to "enum: IN | OUT | PWM",
                "state" to "enum: ON | OFF",
                "initialState" to "enum: ON | OFF",
                "shutdownState" to "enum: ON | OFF",
                "hardwareType" to "enum: GROUND | POWER_3V3 | POWER_5V | DIGITAL | I2C | SPI | SERIAL | PWM | GPCLK",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Server.SerialDevice",
            typeFqn = "krill.zone.shared.KrillApp.Server.SerialDevice",
            metaFqn = "krill.zone.shared.krillapp.server.serialdevice.SerialDeviceMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.server.serialdevice.SerialDeviceMetaData",
                "name" to JsonPrimitive(""),
                "hardwareId" to JsonPrimitive(""),
                "hardwareType" to JsonPrimitive("SERIAL"),
                "status" to JsonPrimitive("CREATED"),
                "interval" to JsonPrimitive(5000L),
                "baudRate" to JsonPrimitive(9600),
                "readTimeout" to JsonPrimitive(1000),
                "writeTimeout" to JsonPrimitive(0),
                "operation" to JsonPrimitive("READ"),
                "dataBits" to JsonPrimitive("EIGHT"),
                "parity" to JsonPrimitive("NONE"),
                "stopBits" to JsonPrimitive("ONE"),
                "flowControl" to JsonPrimitive("NONE"),
                "encoding" to JsonPrimitive("UTF_8"),
                "terminator" to JsonPrimitive("CR"),
                "sendCommand" to JsonPrimitive("R"),
                "sources" to nodeIdentityArray("" to ""),
                "targets" to nodeIdentityArray(),
                "executionSource" to JsonArray(emptyList()),
            ),
            role = "target",
            sideEffect = "high",
            description = "Reads from / writes to a serial port on the host.",
            validParentTypes = listOf("KrillApp.Server"),
            validChildTypes = listOf("KrillApp.DataPoint"),
            metaFieldHints = mapOf(
                "operation" to "enum: READ | WRITE",
                "status" to "enum (NodeState): PAUSED | INFO | WARN | SEVERE | ERROR | PAIRING | NONE | PROCESSING | EXECUTED | DELETING | CREATED | USER_EDIT | USER_SUBMIT | SNAPSHOT_UPDATE | UNAUTHORISED | EDITING",
                "dataBits" to "enum: FIVE | SIX | SEVEN | EIGHT",
                "parity" to "enum: NONE | ODD | EVEN | MARK | SPACE",
                "stopBits" to "enum: ONE | ONE_POINT_FIVE | TWO",
                "flowControl" to "enum: NONE | RTS_CTS | XON_XOFF",
                "encoding" to "enum: ASCII | UTF_8 | ISO_8859_1 | BINARY",
                "terminator" to "enum: CR | LF | CRLF | NONE",
                "hardwareType" to "enum: SERIAL (fixed for this type)",
                "sources" to NODE_IDENTITY_HINT,
                "targets" to NODE_IDENTITY_HINT,
                "executionSource" to "List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>",
            ),
        ),
        KrillNodeType(
            shortName = "KrillApp.Server.Backup",
            typeFqn = "krill.zone.shared.KrillApp.Server.Backup",
            metaFqn = "krill.zone.shared.krillapp.server.backup.BackupMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.server.backup.BackupMetaData",
                "name" to JsonPrimitive(""),
                "backupPath" to JsonPrimitive(""),
                "includeSnapshotData" to JsonPrimitive(true),
                "includeProjectData" to JsonPrimitive(true),
                "includeCameraThumbnails" to JsonPrimitive(true),
                "maxAgeDays" to JsonPrimitive(0),
            ),
            role = "action",
            sideEffect = "high",
            description = "Automated server backup with retention and one-click restore.",
            validParentTypes = listOf("KrillApp.Server"),
            validChildTypes = emptyList(),
        ),
        KrillNodeType(
            shortName = "KrillApp.Server.LLM",
            typeFqn = "krill.zone.shared.KrillApp.Server.LLM",
            metaFqn = "krill.zone.shared.krillapp.server.llm.LLMMetaData",
            defaultMeta = meta(
                "krill.zone.shared.krillapp.server.llm.LLMMetaData",
                "port" to JsonPrimitive(11434),
                "model" to JsonPrimitive("kimi-k2:latest"),
                "chat" to JsonArray(emptyList()),
                "selectedNodes" to nodeIdentityArray(),
            ),
            role = "action",
            sideEffect = "medium",
            description = "Connects to a local Ollama LLM service for AI-assisted automation.",
            validParentTypes = listOf("KrillApp.Server"),
            validChildTypes = emptyList(),
            notes = "LLMMetaData has no `name` field — any `name` arg is dropped on the server.",
            metaFieldHints = mapOf(
                "selectedNodes" to NODE_IDENTITY_HINT + " Nodes the LLM is allowed to read/write as context.",
                "chat" to "List<Message> — Ollama-style {role, content} entries; leave empty for fresh LLMs.",
            ),
        ),
    )

    val byShortName: Map<String, KrillNodeType> = TYPE_TABLE.associateBy { it.shortName }
    val byTypeFqn: Map<String, KrillNodeType> = TYPE_TABLE.associateBy { it.typeFqn }

    fun all(): List<KrillNodeType> = TYPE_TABLE

    /** Resolve a user-supplied selector. Accepts `KrillApp.X.Y`, the FQN, or `X.Y`. */
    fun resolve(selector: String): KrillNodeType? {
        byShortName[selector]?.let { return it }
        byTypeFqn[selector]?.let { return it }
        val prefixed = "KrillApp.${selector.removePrefix("KrillApp.")}"
        return byShortName[prefixed]
    }
}
