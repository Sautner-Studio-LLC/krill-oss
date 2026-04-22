package krill.zone.mcp.mcp.tools

import krill.zone.mcp.krill.KrillClient
import krill.zone.mcp.krill.KrillNodeType
import krill.zone.mcp.krill.KrillNodeTypes
import krill.zone.mcp.krill.KrillRegistry
import krill.zone.mcp.mcp.Tool
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Generic node creation — builds a valid Node payload for any KrillApp.* type
 * in the [KrillNodeTypes] registry and POSTs it to `/node/{id}` with
 * `state="CREATED"`, which the Krill server treats as an upsert-creates.
 *
 * The MetaData layer is a passthrough: `defaultMeta` from the registry sets
 * the polymorphic `type` discriminator and fills in sensible defaults; caller
 * overlays come from the `meta` argument (shallow key-merge). The server runs
 * `ignoreUnknownKeys = true` on its `fastJson`, so extra keys in the `meta`
 * argument are silently dropped — keys missing from a MetaData's schema don't
 * need to be filtered here.
 */
class CreateNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "create_node"
    override val description =
        "Create a Krill node of ANY registered KrillApp.* type. Required: `type` (short name like " +
            "`KrillApp.DataPoint` or full FQN) and `parent` (the id of the parent node on the server). " +
            "Optional: `name` (injected into meta.name when the MetaData class supports it), `meta` (a " +
            "JsonObject whose keys overlay the type's default meta skeleton). Use `list_node_types` to " +
            "discover valid types, their parents, and their meta fields. Prefer the specialized " +
            "`create_project` / `create_diagram` tools for those types (diagram needs file upload)."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("type") {
                put("type", "string")
                put(
                    "description",
                    "KrillApp type short name (e.g. `KrillApp.DataPoint`, `KrillApp.Trigger.HighThreshold`) " +
                        "or full FQN. Call `list_node_types` for the catalog.",
                )
            }
            putJsonObject("parent") {
                put("type", "string")
                put(
                    "description",
                    "Id of the parent node on the same server. For top-level server children (DataPoint, " +
                        "Project, Server.Pin, etc.) pass the server id.",
                )
            }
            putJsonObject("name") {
                put("type", "string")
                put(
                    "description",
                    "Optional display name. Overlayed onto `meta.name` — a few MetaData classes (Mqtt, " +
                        "Compute, Lambda, SMTP, LLM) have no `name` field and silently drop it.",
                )
            }
            putJsonObject("meta") {
                put("type", "object")
                put(
                    "description",
                    "Optional meta-field overlay. Keys are shallow-merged over the type's default meta " +
                        "skeleton. Do NOT set `type` here — the tool writes the correct MetaData " +
                        "discriminator automatically.",
                )
            }
        }
        putJsonArray("required") {
            add("type")
            add("parent")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val typeSelector = arguments["type"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: type")
        val spec = KrillNodeTypes.resolve(typeSelector)
            ?: error(
                "Unknown node type '$typeSelector'. Call `list_node_types` for the catalog, or pass a full FQN " +
                    "like `krill.zone.shared.KrillApp.DataPoint`.",
            )

        val parentId = arguments["parent"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: parent")
        val callerName = arguments["name"]?.jsonPrimitive?.contentOrNull
        val callerMeta = arguments["meta"] as? JsonObject

        val warnings = mutableListOf<String>()

        // Verify parent exists on the server.
        val parentNode = runCatching { client.node(parentId) as? JsonObject }.getOrNull()
            ?: error(
                "Parent node '$parentId' not found on server ${client.serverId}. Verify with `list_nodes` " +
                    "or `get_node` before creating.",
            )
        val parentTypeFqn = parentNode["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        val parentShortName = parentTypeFqn?.let { KrillNodeTypes.byTypeFqn[it]?.shortName }

        if (spec.validParentTypes.isNotEmpty() && parentShortName != null &&
            parentShortName !in spec.validParentTypes
        ) {
            warnings += "Parent type $parentShortName is not in ${spec.shortName}'s valid parent list " +
                "(${spec.validParentTypes}). The server may still accept it; confirm with the user."
        }

        val mergedMeta = buildMergedMeta(spec, callerName, callerMeta)

        val newId = UUID.randomUUID().toString()
        val node = buildJsonObject {
            put("id", newId)
            put("parent", parentId)
            put("host", client.serverId)
            putJsonObject("type") { put("type", spec.typeFqn) }
            put("state", "CREATED")
            put("meta", mergedMeta)
            put("timestamp", 0L)
        }
        client.postNode(node)

        return buildJsonObject {
            put("server", client.serverId)
            put("nodeId", newId)
            put("type", spec.shortName)
            put("parent", parentId)
            putJsonObject("parentType") {
                put("fqn", parentTypeFqn ?: "")
                put("shortName", parentShortName ?: "")
            }
            put("meta", mergedMeta)
            if (warnings.isNotEmpty()) {
                putJsonArray("warnings") { warnings.forEach { add(JsonPrimitive(it)) } }
            }
        }
    }

    /**
     * Default meta + caller overlay + caller-supplied top-level `name`. The
     * discriminator (`type` key) always comes from the registry — callers
     * cannot override it, which would produce a node the server can't
     * deserialize into the matching KrillApp subtype.
     */
    private fun buildMergedMeta(
        spec: KrillNodeType,
        callerName: String?,
        callerMeta: JsonObject?,
    ): JsonObject {
        val merged = spec.defaultMeta.toMutableMap()
        if (callerMeta != null) {
            for ((key, value) in callerMeta) {
                if (key == KrillNodeTypes.META_TYPE_KEY) continue
                merged[key] = value
            }
        }
        if (callerName != null) merged["name"] = JsonPrimitive(callerName)
        merged[KrillNodeTypes.META_TYPE_KEY] = JsonPrimitive(spec.metaFqn)
        return JsonObject(merged)
    }
}

/**
 * Returns the static [KrillNodeTypes] registry so agents can discover the
 * creatable type surface from the live MCP — no need to have the companion
 * skill's static catalog loaded first.
 */
class ListNodeTypesTool(@Suppress("unused") private val registry: KrillRegistry) : Tool {
    override val name = "list_node_types"
    override val description =
        "List every KrillApp.* node type the MCP knows how to create via `create_node`: short name, " +
            "MetaData class, role, side-effect level, description, valid parent/child types, and default " +
            "meta skeleton. Call this first when authoring a node graph from a description."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("role") {
                put("type", "string")
                put(
                    "description",
                    "Optional case-insensitive filter on `role` (state, trigger, action, filter, display, " +
                        "container, infra, logic, transform, sensor, target).",
                )
            }
            putJsonObject("contains") {
                put("type", "string")
                put("description", "Optional case-insensitive substring match against the short name or description.")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val roleFilter = arguments["role"]?.jsonPrimitive?.contentOrNull?.lowercase()
        val textFilter = arguments["contains"]?.jsonPrimitive?.contentOrNull?.lowercase()

        val rows = KrillNodeTypes.all().filter { t ->
            (roleFilter == null || t.role.lowercase() == roleFilter) &&
                (
                    textFilter == null ||
                        textFilter in t.shortName.lowercase() ||
                        textFilter in t.description.lowercase()
                    )
        }

        return buildJsonObject {
            put("count", rows.size)
            putJsonArray("types") {
                rows.forEach { t ->
                    addJsonObject {
                        put("shortName", t.shortName)
                        put("typeFqn", t.typeFqn)
                        put("metaFqn", t.metaFqn)
                        put("role", t.role)
                        put("sideEffect", t.sideEffect)
                        put("description", t.description)
                        putJsonArray("validParentTypes") { t.validParentTypes.forEach { add(JsonPrimitive(it)) } }
                        putJsonArray("validChildTypes") { t.validChildTypes.forEach { add(JsonPrimitive(it)) } }
                        put("defaultMeta", t.defaultMeta)
                        if (t.notes != null) put("notes", t.notes)
                    }
                }
            }
        }
    }
}

/**
 * Records one or many snapshots on an existing DataPoint.
 *
 * Wire shape: each recorded value is a `Snapshot { timestamp: Long, value: String }`
 * living at `node.meta.snapshot`. Posting the DataPoint with
 * `state = USER_EDIT` routes through `ServerDataPointProcessor.post → process
 * → DataProcessor.ingestDataPointValue`, which runs filters, appends to the
 * time-series store, and fires downstream triggers. Any other state (NONE,
 * CREATED, etc.) is persisted as a node update only — no time-series point.
 *
 * Client-side validation mirrors the server's `validateDataType`: if the
 * server would silently drop a value because it doesn't match the DataPoint's
 * `dataType`, we fail here instead — the POST returns 202 with an empty body,
 * so server-side rejection is invisible to the caller.
 */
class RecordSnapshotTool(private val registry: KrillRegistry) : Tool {
    override val name = "record_snapshot"
    override val description =
        "Record one or many snapshot values on an existing DataPoint. Pass either a single `value` " +
            "(with optional `timestamp`) or a `snapshots` array of {timestamp, value} pairs for a " +
            "backfill / series. Values are coerced to string on the wire and validated client-side " +
            "against the DataPoint's `dataType`: TEXT non-empty; DIGITAL ∈ {0, 1} (booleans are " +
            "mapped to 0/1); DOUBLE parseable; COLOR parseable as Long; JSON non-empty. Each " +
            "accepted snapshot is posted with `state=USER_EDIT` so the server's filter/trigger " +
            "pipeline evaluates it."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("dataPointId") {
                put("type", "string")
                put("description", "Id of the target KrillApp.DataPoint node.")
            }
            putJsonObject("value") {
                put(
                    "description",
                    "Single-value shortcut. String, number, or boolean. Mutually exclusive with `snapshots`.",
                )
            }
            putJsonObject("timestamp") {
                put("type", "integer")
                put(
                    "description",
                    "Epoch millis for the single-value shortcut. Defaults to server-received `now` if omitted.",
                )
            }
            putJsonObject("snapshots") {
                put("type", "array")
                put(
                    "description",
                    "Batch form — array of {timestamp: epoch-millis, value}. Posts are submitted in array " +
                        "order; each POST returns 202 before filter/trigger evaluation completes.",
                )
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("timestamp") { put("type", "integer") }
                        putJsonObject("value") {}
                    }
                    putJsonArray("required") { add("timestamp"); add("value") }
                }
            }
        }
        putJsonArray("required") { add("dataPointId") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val dataPointId = arguments["dataPointId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: dataPointId")

        val existing = client.node(dataPointId) as? JsonObject
            ?: error("DataPoint $dataPointId not found on server ${client.serverId}.")
        val existingType = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (existingType != "krill.zone.shared.KrillApp.DataPoint") {
            error("Node $dataPointId is not a KrillApp.DataPoint (got $existingType).")
        }
        val existingMeta = existing["meta"] as? JsonObject
            ?: error("DataPoint $dataPointId has no meta object.")
        val dataType = existingMeta["dataType"]?.jsonPrimitive?.contentOrNull ?: "DOUBLE"

        val inputs: List<RawSnapshot> = readInputs(arguments)
        if (inputs.isEmpty()) {
            error(
                "Provide either `value` (with optional `timestamp`) or a non-empty `snapshots` array.",
            )
        }

        // Validate every snapshot before posting any of them — refuse to
        // half-apply a backfill that has a bad value in the middle.
        val validated = inputs.mapIndexed { index, raw ->
            val stringValue = coerceValue(raw.value, dataType)
                ?: error("Snapshot #$index value ${raw.value} is not valid for dataType=$dataType.")
            ValidSnapshot(
                timestamp = raw.timestamp ?: System.currentTimeMillis(),
                value = stringValue,
            )
        }

        val postedAt = mutableListOf<ValidSnapshot>()
        for (snap in validated) {
            val snapshotObj = buildJsonObject {
                put("timestamp", snap.timestamp)
                put("value", snap.value)
            }
            val updatedMeta = JsonObject(
                existingMeta.toMutableMap().apply { put("snapshot", snapshotObj) },
            )
            val updatedNode = JsonObject(
                existing.toMutableMap().apply {
                    put("meta", updatedMeta)
                    put("state", JsonPrimitive("USER_EDIT"))
                },
            )
            client.postNode(updatedNode)
            postedAt += snap
        }

        return buildJsonObject {
            put("server", client.serverId)
            put("dataPointId", dataPointId)
            put("dataType", dataType)
            put("submitted", postedAt.size)
            putJsonArray("snapshots") {
                postedAt.forEach { s ->
                    addJsonObject {
                        put("timestamp", s.timestamp)
                        put("value", s.value)
                    }
                }
            }
            put(
                "note",
                "Each POST returns 202 immediately; the server ingests asynchronously and may reject a " +
                    "snapshot via child filters. Call `read_series` to verify persistence.",
            )
        }
    }

    private data class RawSnapshot(val timestamp: Long?, val value: JsonElement)
    private data class ValidSnapshot(val timestamp: Long, val value: String)

    private fun readInputs(arguments: JsonObject): List<RawSnapshot> {
        val batch = arguments["snapshots"] as? JsonArray
        if (batch != null) {
            if (arguments.containsKey("value") || arguments.containsKey("timestamp")) {
                error("Pass either `snapshots` or the `value`/`timestamp` shortcut — not both.")
            }
            return batch.mapIndexed { index, el ->
                val obj = el as? JsonObject
                    ?: error("snapshots[$index] must be an object {timestamp, value}.")
                val ts = obj["timestamp"]?.jsonPrimitive?.longOrNull
                    ?: error("snapshots[$index].timestamp is required (epoch millis).")
                val v = obj["value"]
                    ?: error("snapshots[$index].value is required.")
                RawSnapshot(ts, v)
            }
        }
        val single = arguments["value"] ?: return emptyList()
        val ts = arguments["timestamp"]?.jsonPrimitive?.longOrNull
        return listOf(RawSnapshot(ts, single))
    }

    /**
     * Mirrors the server's `DataProcessor.validateDataType` with friendly
     * coercions: booleans → "0"/"1" for DIGITAL, numbers → their decimal
     * string everywhere else. Returns null on mismatch so the caller can
     * report which snapshot and what dataType failed.
     */
    private fun coerceValue(raw: JsonElement, dataType: String): String? {
        val primitive = raw as? JsonPrimitive ?: return null
        val content = primitive.content
        return when (dataType) {
            "TEXT" -> content.takeIf { it.isNotEmpty() }
            "JSON" -> content.takeIf { it.isNotEmpty() }
            "DIGITAL" -> when {
                primitive.booleanOrNull == true -> "1"
                primitive.booleanOrNull == false -> "0"
                else -> content.toDoubleOrNull()?.takeIf { it == 0.0 || it == 1.0 }?.let {
                    if (it == 1.0) "1" else "0"
                }
            }
            "DOUBLE" -> content.toDoubleOrNull()?.toString()
            "COLOR" -> content.toLongOrNull()?.toString()
            else -> content
        }
    }
}

private fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    return registry.resolve(selector)
        ?: error(
            "No Krill server matches '$selector' (and no default is registered). Try `reseed_servers` — " +
                "the registry may have missed the initial probe.",
        )
}
