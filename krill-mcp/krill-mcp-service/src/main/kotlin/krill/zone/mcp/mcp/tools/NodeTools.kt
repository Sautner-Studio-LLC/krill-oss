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
 * `state="CREATE_OR_OVERWRITE"`, which the Krill server treats as an
 * upsert-creates. (`CREATED` no longer exists in the NodeState enum — posting
 * it fails Node deserialization with a 400.)
 *
 * The MetaData layer is a passthrough: `defaultMeta` from the registry sets
 * the polymorphic `type` discriminator and fills in sensible defaults; caller
 * overlays come from the `meta` argument (shallow key-merge). The server runs
 * `ignoreUnknownKeys = true` on its `fastJson`, so extra keys in the `meta`
 * argument are silently dropped — keys missing from a MetaData's schema don't
 * need to be filtered here.
 *
 * Wiring note: parent/child is visual organization only. On creation the
 * server wires the new node's parent into `meta.sources` + SOURCE_INVOKED
 * when `sources` is empty (and neither side is a Project/Server container),
 * so a child observes its parent by default. Pass explicit `sources` /
 * `inputs` / `invocationTriggers` in `meta` (or call `set_node_wiring`
 * afterwards) to wire anything else.
 */
class CreateNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "create_node"
    override val description =
        "Create a Krill node of ANY registered KrillApp.* type. Required: `type` (short name like " +
            "`KrillApp.DataPoint` or full FQN). Optional: `parent` (id of the parent node; omit or pass " +
            "the server id to create a top-level node under the server root), `name` (injected into " +
            "meta.name when the MetaData class supports it), `meta` (a JsonObject whose keys overlay the " +
            "type's default meta skeleton). Use `list_node_types` to discover valid types, their parents, " +
            "and their meta fields. Parent/child is visual organization only — data flows through observer " +
            "wiring (`meta.sources` + `meta.invocationTriggers`, values read via `meta.inputs`). When " +
            "`sources` is left empty the server wires the parent in as the default source, so a child " +
            "observes its parent out of the box; use `set_node_wiring` to rewire. Prefer the specialized " +
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
                    "Parent node id (UUID) or display name (`meta.name`). Omit (or pass the server id) to " +
                        "create a top-level node directly under the server root. When a plain name is " +
                        "passed (not a UUID), the tool resolves it via `list_nodes` — it must be an " +
                        "exact case-insensitive match of an existing node's `meta.name`.",
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

        val rawParent = arguments["parent"]?.jsonPrimitive?.contentOrNull
        val callerName = arguments["name"]?.jsonPrimitive?.contentOrNull
        val callerMeta = arguments["meta"] as? JsonObject

        // Resolve the parent node. Three cases:
        //   1. Absent or equals the server id → server-root synthetic stub (krill-oss#163).
        //   2. Looks like a UUID → fetch directly from the server.
        //   3. Plain name → search all nodes by meta.name (krill-oss#168).
        val parentId: String
        val parentNode: JsonObject

        when {
            rawParent == null || rawParent == client.serverId -> {
                parentId = client.serverId
                parentNode = serverParentNode(client.serverId)
            }
            isUuid(rawParent) -> {
                parentId = rawParent
                parentNode = runCatching { client.node(rawParent) as? JsonObject }.getOrNull()
                    ?: error(
                        "Parent node '$rawParent' not found on server ${client.serverId}. Verify with " +
                            "`list_nodes` or `get_node` before creating.",
                    )
            }
            else -> {
                val matched = resolveNodeByName(client.nodes(), rawParent)
                    ?: error(
                        "No node with name '$rawParent' found on server ${client.serverId}. " +
                            "Pass a UUID or verify the name with `list_nodes`.",
                    )
                parentId = matched["id"]?.jsonPrimitive?.contentOrNull
                    ?: error("Matched parent node for name '$rawParent' has no id field.")
                parentNode = matched
            }
        }
        val parentTypeFqn = parentNode["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        val parentShortName = parentTypeFqn?.let { KrillNodeTypes.byTypeFqn[it]?.shortName }
        val resolvedCallerName = callerName ?: derivedDefaultName(spec, parentNode)

        // Deterministic parent-type validation. Status values:
        //   ok                    — parentShortName is in spec.validParentTypes
        //   warn                  — known parent type that isn't in the valid list
        //   unknown-parent-type   — parent node has a type FQN this MCP's registry doesn't know
        //   no-constraint         — spec.validParentTypes is empty (container / root types)
        val parentValidationStatus = when {
            spec.validParentTypes.isEmpty() -> "no-constraint"
            parentShortName == null -> "unknown-parent-type"
            parentShortName in spec.validParentTypes -> "ok"
            else -> "warn"
        }
        val parentValidationWarning = if (parentValidationStatus == "warn") {
            "Parent type $parentShortName is not in ${spec.shortName}'s valid parent list " +
                "(${spec.validParentTypes}). The server will still accept the post; confirm with the user."
        } else null

        val mergedMeta = buildMergedMeta(spec, resolvedCallerName, callerMeta)

        val newId = UUID.randomUUID().toString()
        val node = buildJsonObject {
            put("id", newId)
            put("parent", parentId)
            put("host", client.serverId)
            putJsonObject("type") { put("type", spec.typeFqn) }
            put("state", "CREATE_OR_OVERWRITE")
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
            putJsonObject("parentValidation") {
                put("status", parentValidationStatus)
                put("parentShortName", parentShortName ?: "")
                putJsonArray("expected") { spec.validParentTypes.forEach { add(JsonPrimitive(it)) } }
                if (parentValidationWarning != null) put("warning", parentValidationWarning)
            }
            put("meta", mergedMeta)
            if (parentValidationWarning != null) {
                // Keep the legacy `warnings[]` alongside `parentValidation.warning` so callers that
                // were already scanning for warnings keep working.
                putJsonArray("warnings") { add(JsonPrimitive(parentValidationWarning)) }
            }
        }
    }

    /** True when [s] is a well-formed UUID — the usual form of a Krill node id. */
    internal fun isUuid(s: String): Boolean = runCatching { UUID.fromString(s) }.isSuccess

    /**
     * Searches [nodes] for the first entry whose `meta.name` equals [name]
     * (case-insensitive). Returns `null` when not found.
     */
    internal fun resolveNodeByName(nodes: JsonArray, name: String): JsonObject? =
        nodes.firstOrNull { element ->
            val obj = element as? JsonObject ?: return@firstOrNull false
            (obj["meta"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                ?.equals(name, ignoreCase = true) == true
        } as? JsonObject

    /**
     * Minimal server-typed parent node JSON used when the caller's `parent`
     * argument equals the server's own id. The Krill server is not addressable
     * via `GET /node/{id}`, so we synthesize a stand-in rather than failing
     * the parent existence check. The FQN is the canonical wire discriminator
     * for the server entity; `KrillNodeTypes.byTypeFqn` won't resolve it
     * (the server type is never in the creatable-type registry), so downstream
     * parent-type validation reports `"unknown-parent-type"` — no warning, no
     * block.  See krill-oss#163.
     */
    internal fun serverParentNode(serverId: String): JsonObject = buildJsonObject {
        put("id", serverId)
        putJsonObject("type") { put("type", "krill.zone.shared.KrillApp.Server") }
        putJsonObject("meta") { put("name", "") }
    }

    /**
     * For node types whose default name would collide across siblings (e.g.
     * `KrillApp.DataPoint.Graph`), derive a parent-aware default from the
     * verified parent node. Returns `null` when no derivation applies — the
     * caller then falls through to the registry's `defaultMeta.name`.
     *
     * Graph rule: `"<parent DataPoint name> graph"` (parent is always a
     * DataPoint per `validParentTypes`). Empty parent name yields `null` so
     * we don't emit a leading-space artefact like `" graph"`.
     */
    internal fun derivedDefaultName(spec: KrillNodeType, parentNode: JsonObject): String? {
        if (spec.shortName != "KrillApp.DataPoint.Graph") return null
        val parentName = (parentNode["meta"] as? JsonObject)
            ?.get("name")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return "$parentName graph"
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
                        if (t.metaFieldHints.isNotEmpty()) {
                            putJsonObject("metaFieldHints") {
                                t.metaFieldHints.forEach { (k, v) -> put(k, v) }
                            }
                        }
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
 * time-series store, and invokes every node observing the DataPoint as a
 * source. Any other state (NONE, CREATE_OR_OVERWRITE, etc.) is persisted as a
 * node update only — no time-series point.
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
            "mapped to 0/1); DOUBLE parseable; COLOR a decimal 24-bit RGB int (R<<16|G<<8|B); " +
            "JSON non-empty. Each accepted snapshot is posted with `state=USER_EDIT` so the " +
            "server's filter/trigger pipeline evaluates it. " +
            "Verification note: the first `read_series` immediately after this call commonly " +
            "returns 0 snapshots even on bare DataPoints — the server ingest runs in `scope.launch` " +
            "and isn't committed yet. Wait ~1.5s or retry once. Persistent zeros after retry mean a " +
            "child Filter rejected the write."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("id") {
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
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")

        val existing = client.node(id) as? JsonObject
            ?: error("DataPoint $id not found on server ${client.serverId}.")
        val existingType = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (existingType != "krill.zone.shared.KrillApp.DataPoint") {
            error("Node $id is not a KrillApp.DataPoint (got $existingType).")
        }
        val existingMeta = existing["meta"] as? JsonObject
            ?: error("DataPoint $id has no meta object.")
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
            put("id", id)
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

/**
 * Delete a node by id. Hits the existing Krill REST route `DELETE /node/{id}`,
 * which the server implements as a **recursive cascade** — deleting a Project
 * tears down its Diagrams, TaskLists, Journals, Cameras in one call; deleting
 * a DataPoint removes its Filters, Graphs, Triggers, Executors transitively.
 * Useful for automated regression-test teardown without reaching for the
 * Krill app UI.
 *
 * The server sets `state = DELETING`, broadcasts a DELETED SSE event, then
 * recursively deletes children. For this MCP, we fetch the node first and
 * pass the whole body back as the DELETE payload — the server signature
 * requires it.
 */
class DeleteNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "delete_node"
    override val description =
        "Delete a node by id. This is a **recursive cascade** on the server — a Project delete " +
            "removes every child it contains; a DataPoint delete removes all its Filters/Graphs/" +
            "Triggers/Executors; and so on. There is no soft-delete, no undo, no tombstone — the " +
            "node and its subtree are gone. Prefer this over `curl DELETE` when scripting teardown."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("id") {
                put("type", "string")
                put("description", "Id of the node to delete. Works for any KrillApp type including Projects (cascades).")
            }
            putJsonObject("confirm") {
                put("type", "boolean")
                put(
                    "description",
                    "Required `true` safety interlock. The tool refuses to delete without it — prevents " +
                        "agents from firing a cascade on a single-word user prompt.",
                )
            }
        }
        putJsonArray("required") {
            add("id")
            add("confirm")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")
        val confirm = arguments["confirm"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!confirm) {
            error(
                "Refusing to delete without `confirm: true`. Pass confirm=true once the user has " +
                    "explicitly acknowledged this is a cascading delete.",
            )
        }

        val existing = client.node(id) as? JsonObject
            ?: error("Node $id not found on server ${client.serverId}.")
        val typeFqn = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        val shortName = typeFqn?.let { KrillNodeTypes.byTypeFqn[it]?.shortName } ?: typeFqn ?: "unknown"
        val name = (existing["meta"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull

        client.deleteNode(existing)

        return buildJsonObject {
            put("server", client.serverId)
            put("deletedId", id)
            put("type", shortName)
            if (name != null) put("name", name)
            put(
                "note",
                "Server cascaded deletes to children. Call `list_nodes` to confirm the subtree is gone — " +
                    "the DELETE returns 200 synchronously but SSE broadcasts and any cross-peer " +
                    "replication can be moments behind.",
            )
        }
    }
}

/**
 * Updates the [NodeAction] verb on any node.
 *
 * Post-unify-source-verb-wiring every MetaData type implements [ActionNodeMetaData],
 * so nodeAction is universal. Patches `meta.nodeAction` and POSTs back with
 * `state=USER_EDIT`. Reading the current action is covered by `get_node`.
 *
 * For updating sources/inputs/invocationTriggers alongside nodeAction in one
 * call, prefer [SetNodeWiringTool].
 */
class SetNodeActionTool(private val registry: KrillRegistry) : Tool {
    override val name = "set_node_action"
    override val description =
        "Set the action verb a Krill node sends downstream when it fires. Every node type carries " +
            "`nodeAction` (all MetaData implements ActionNodeMetaData). The verb cascades source → " +
            "observer: `EXECUTE` (default) runs each observer's primary logic; `RESET` tells observers " +
            "to revert to initial/cleared state — TaskList: marks all tasks complete and reopens " +
            "repeatables; Trigger family: clears alarm WARN→NONE without re-evaluating the threshold. " +
            "A node with no sensible response to a verb safely ignores it (best effort). " +
            "To also set sources/inputs/invocationTriggers in one call, use `set_node_wiring`. " +
            "Use `get_node` to read the current `meta.nodeAction` before calling."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "Id of the node to update.")
            }
            putJsonObject("action") {
                put("type", "string")
                put("description", "Action verb: EXECUTE (default) or RESET.")
            }
        }
        putJsonArray("required") {
            add("id")
            add("action")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")
        val action = arguments["action"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: action")

        if (action !in VALID_ACTIONS) {
            error("Invalid action '$action'. Must be one of: ${VALID_ACTIONS.joinToString()}.")
        }

        val client = resolve(registry, arguments)
        val existing = client.node(id) as? JsonObject
            ?: error("Node $id not found on server ${client.serverId}.")

        val typeFqn = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        val spec = typeFqn?.let { KrillNodeTypes.byTypeFqn[it] }

        val existingMeta = existing["meta"] as? JsonObject
            ?: error("Node $id has no meta object.")

        val updatedMeta = JsonObject(existingMeta.toMutableMap().apply { put("nodeAction", JsonPrimitive(action)) })
        val updatedNode = JsonObject(
            existing.toMutableMap().apply {
                put("meta", updatedMeta)
                put("state", JsonPrimitive("USER_EDIT"))
            },
        )
        client.postNode(updatedNode)

        return buildJsonObject {
            put("server", client.serverId)
            put("id", id)
            put("type", spec?.shortName ?: typeFqn ?: "unknown")
            put("nodeAction", action)
            put(
                "note",
                "Posted with state=USER_EDIT. Verify via `get_node` — the server persists meta " +
                    "asynchronously; allow ~500ms before reading back.",
            )
        }
    }

    companion object {
        val VALID_ACTIONS = setOf("EXECUTE", "RESET")
    }
}

/**
 * Sets observer wiring (sources / inputs / invocationTriggers) and the action
 * verb on any Krill node.
 *
 * Post-unify-source-verb-wiring every MetaData type implements
 * [krill.zone.shared.node.SourceMetaData], so sources, inputs,
 * invocationTriggers, and nodeAction are universal. Supply one or more of the
 * four fields; unset fields are left unchanged on the existing node.
 *
 * There is no `targets` field and no push path — wiring lives on the node
 * that observes (or reads), never on the node being observed. To make node B
 * react to node A, update **B** with `sources: [A]` and
 * `invocationTriggers: ["SOURCE_INVOKED"]`.
 *
 * Reading the current wiring is handled by `get_node` — meta.sources /
 * meta.inputs / meta.invocationTriggers / meta.nodeAction are returned
 * verbatim.
 */
class SetNodeWiringTool(private val registry: KrillRegistry) : Tool {
    override val name = "set_node_wiring"
    override val description =
        "Set observer wiring and action verb on any Krill node. Every node type carries sources, " +
            "inputs, invocationTriggers, and nodeAction (all MetaData implements SourceMetaData). " +
            "Supply one or more fields; omitted fields are left unchanged. Wiring lives on the node " +
            "that OBSERVES: to make node B react to node A, update B with `sources: [A]` and " +
            "`invocationTriggers: [\"SOURCE_INVOKED\"]` — there is no targets field and no push path. " +
            "`sources` (who wakes me) and `inputs` (whose last result I read when I run) are arrays " +
            "of {nodeId, hostId} identity pairs — a node consuming another's output usually lists it " +
            "in BOTH. `invocationTriggers` values: SOURCE_INVOKED (a listed source completed) and " +
            "ON_CLICK (manual tap); pass [] to disable auto-fire. `nodeAction` is EXECUTE (default) " +
            "or RESET — the verb cascades from source to observer when the node fires. " +
            "Read current wiring via `get_node`. Posted with state=USER_EDIT; allow ~500ms for " +
            "the server to persist before reading back."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "Id of the node to update — the OBSERVER side of the wiring.")
            }
            putJsonObject("sources") {
                put("type", "array")
                put(
                    "description",
                    "Nodes this node observes. When a source completes its work, this node is invoked " +
                        "(requires SOURCE_INVOKED in invocationTriggers). " +
                        "Each entry: {nodeId: string, hostId: string}. Pass [] to clear.",
                )
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("nodeId") {
                            put("type", "string")
                            put("description", "UUID of the source node.")
                        }
                        putJsonObject("hostId") {
                            put("type", "string")
                            put("description", "Server UUID that owns the source node.")
                        }
                    }
                    putJsonArray("required") { add("nodeId"); add("hostId") }
                }
            }
            putJsonObject("inputs") {
                put("type", "array")
                put(
                    "description",
                    "Nodes whose last result (meta.snapshot) this node reads when it executes — e.g. a " +
                        "Calculation's formula variables, or the node a DataPoint ingests from. Inputs " +
                        "never wake the node; wake-up is sources + invocationTriggers. " +
                        "Each entry: {nodeId: string, hostId: string}. Pass [] to clear.",
                )
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("nodeId") { put("type", "string") }
                        putJsonObject("hostId") { put("type", "string") }
                    }
                    putJsonArray("required") { add("nodeId"); add("hostId") }
                }
            }
            putJsonObject("invocationTriggers") {
                put("type", "array")
                put(
                    "description",
                    "Events that invoke this node. Valid values: SOURCE_INVOKED (a node listed in " +
                        "sources completed its work), ON_CLICK (manual user tap). Pass [] to disable auto-fire.",
                )
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("nodeAction") {
                put("type", "string")
                put(
                    "description",
                    "Verb this node sends downstream when it fires: EXECUTE (default) or RESET. " +
                        "The verb cascades — observers apply the source's verb.",
                )
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")

        val nodeAction = arguments["nodeAction"]?.jsonPrimitive?.contentOrNull
        if (nodeAction != null && nodeAction !in VALID_ACTIONS) {
            error("Invalid nodeAction '$nodeAction'. Must be one of: ${VALID_ACTIONS.joinToString()}.")
        }

        val invocationTriggers = arguments["invocationTriggers"] as? JsonArray
        if (invocationTriggers != null) {
            val invalid = invocationTriggers
                .mapNotNull { it.jsonPrimitive.contentOrNull }
                .filter { it !in VALID_INVOCATION_TRIGGERS }
            if (invalid.isNotEmpty()) {
                error(
                    "Invalid invocationTriggers value(s): ${invalid.joinToString()}. " +
                        "Valid values: ${VALID_INVOCATION_TRIGGERS.joinToString()}.",
                )
            }
        }

        // Catch callers still speaking the pre-unify schema with a pointed hint
        // instead of a silent no-op (the server would just drop the unknown keys).
        if (arguments.containsKey("targets")) {
            error(
                "`targets` no longer exists — wiring lives on the observer. To make node B react to " +
                    "this node, call set_node_wiring on B with sources=[this node] and " +
                    "invocationTriggers=[\"SOURCE_INVOKED\"] (add it to B's inputs too if B reads its value).",
            )
        }
        if (arguments.containsKey("executionSource")) {
            error(
                "`executionSource` was renamed — pass `invocationTriggers` with values " +
                    "SOURCE_INVOKED and/or ON_CLICK.",
            )
        }

        val updates = mutableMapOf<String, JsonElement>()
        arguments["sources"]?.let { updates["sources"] = it }
        arguments["inputs"]?.let { updates["inputs"] = it }
        invocationTriggers?.let { updates["invocationTriggers"] = it }
        nodeAction?.let { updates["nodeAction"] = JsonPrimitive(it) }

        if (updates.isEmpty()) {
            error("Provide at least one of: sources, inputs, invocationTriggers, nodeAction.")
        }

        val client = resolve(registry, arguments)
        val existing = client.node(id) as? JsonObject
            ?: error("Node $id not found on server ${client.serverId}.")

        val typeFqn = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        val shortName = typeFqn?.let { KrillNodeTypes.byTypeFqn[it]?.shortName } ?: typeFqn ?: "unknown"

        val existingMeta = existing["meta"] as? JsonObject
            ?: error("Node $id has no meta object.")

        val updatedMeta = JsonObject(existingMeta.toMutableMap().also { it.putAll(updates) })
        val updatedNode = JsonObject(
            existing.toMutableMap().also {
                it["meta"] = updatedMeta
                it["state"] = JsonPrimitive("USER_EDIT")
            },
        )
        client.postNode(updatedNode)

        return buildJsonObject {
            put("server", client.serverId)
            put("id", id)
            put("type", shortName)
            updates.forEach { (k, v) -> put(k, v) }
            put(
                "note",
                "Posted with state=USER_EDIT. Verify via `get_node` — the server persists meta " +
                    "asynchronously; allow ~500ms before reading back.",
            )
        }
    }

    companion object {
        val VALID_ACTIONS = setOf("EXECUTE", "RESET")
        val VALID_INVOCATION_TRIGGERS = setOf("SOURCE_INVOKED", "ON_CLICK")
    }
}

/**
 * Updates an existing node's metadata by shallow-merging a caller-supplied
 * meta overlay onto the current node and re-posting with `state=USER_EDIT`.
 *
 * This is the create-then-configure counterpart to [CreateNodeTool]: after
 * creating a node you can call `update_node` to set fields that couldn't be
 * determined at creation time — e.g. a CronTimer's `expression`, a
 * Calculation's `formula`, or a node's `name`.
 *
 * The polymorphic `type` key in meta is always preserved from the existing
 * node — callers cannot change a node's type after creation.
 */
class UpdateNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "update_node"
    override val description =
        "Update an existing node's metadata by shallow-merging a `meta` overlay onto the current " +
            "node and posting it back with `state=USER_EDIT` so connected clients (desktop UI, other " +
            "agents) see the change live. Use this to configure fields that couldn't be set at " +
            "create_node time: a CronTimer's `expression`, a Calculation's `formula`, a node's `name`, " +
            "or any other MetaData field. Wiring fields (`sources`, `inputs`, `invocationTriggers`, " +
            "`nodeAction`) can also be patched here, but `set_node_wiring` and `set_node_action` are " +
            "preferred for those — they document the intent more clearly. The polymorphic `type` key " +
            "inside meta is always preserved from the existing node; passing it is silently ignored."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "Id of the node to update.")
            }
            putJsonObject("meta") {
                put("type", "object")
                put(
                    "description",
                    "Meta-field overlay. Keys are shallow-merged over the node's current meta. The " +
                        "polymorphic `type` key is always taken from the existing node — pass it or not, " +
                        "it makes no difference.",
                )
            }
        }
        putJsonArray("required") {
            add("id")
            add("meta")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")
        val overlay = arguments["meta"] as? JsonObject
            ?: error("Missing required argument: meta (must be a JSON object of fields to update).")
        if (overlay.isEmpty()) {
            error("meta is empty — provide at least one field to update (e.g. {\"expression\": \"*/5 * * * * *\"}).")
        }

        val client = resolve(registry, arguments)
        val existing = client.node(id) as? JsonObject
            ?: error("Node $id not found on server ${client.serverId}.")

        val typeFqn = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        val shortName = typeFqn?.let { KrillNodeTypes.byTypeFqn[it]?.shortName } ?: typeFqn ?: "unknown"

        val existingMeta = existing["meta"] as? JsonObject
            ?: error("Node $id has no meta object.")

        val updatedMeta = JsonObject(
            existingMeta.toMutableMap().also { base ->
                for ((key, value) in overlay) {
                    if (key == KrillNodeTypes.META_TYPE_KEY) continue
                    base[key] = value
                }
            },
        )
        val updatedNode = JsonObject(
            existing.toMutableMap().also {
                it["meta"] = updatedMeta
                it["state"] = JsonPrimitive("USER_EDIT")
            },
        )
        client.postNode(updatedNode)

        val appliedKeys = overlay.keys.filter { it != KrillNodeTypes.META_TYPE_KEY }
        return buildJsonObject {
            put("server", client.serverId)
            put("id", id)
            put("type", shortName)
            putJsonArray("updatedFields") { appliedKeys.forEach { add(JsonPrimitive(it)) } }
            put("meta", updatedMeta)
            put(
                "note",
                "Posted with state=USER_EDIT. Connected clients receive the update via SSE. " +
                    "Verify via `get_node` — allow ~500ms for the server to persist before reading back.",
            )
        }
    }
}

/**
 * Sets a value on a DataPoint by id or display name.
 *
 * The demo orchestrator uses a `set_value` action (target name + scalar value)
 * that has no direct MCP equivalent until this tool — the closest existing tool
 * was `set_node_wiring`, which modifies observer wiring rather than recording a
 * snapshot. This caused the demo to fail with a 404 on `set_node_wiring` whenever
 * it tried to feed live data into a pipeline node by name (krill-oss#174).
 *
 * Target resolution mirrors `create_node`'s parent resolution:
 *   - UUID → direct `GET /node/{id}`
 *   - plain name → search via `list_nodes`, case-insensitive `meta.name` match
 *
 * Value coercion and `state=USER_EDIT` posting mirror [RecordSnapshotTool] — the
 * server's filter/trigger pipeline evaluates every `USER_EDIT` post. Use
 * `record_snapshot` when you have a UUID and need batch/backfill semantics; use
 * `set_value` for one-shot imperative writes where only the node name is known.
 */
class SetValueTool(private val registry: KrillRegistry) : Tool {
    override val name = "set_value"
    override val description =
        "Record a value on a DataPoint — the primary way for demo scripts and automation to feed live " +
            "data into a Krill pipeline when only the node's human name is known. Accepts a `target` " +
            "that is either a node id (UUID) or a display name (`meta.name`); when a name is passed the " +
            "tool resolves it via `list_nodes` (exact case-insensitive match). Validates the resolved " +
            "node is a DataPoint, then coerces and validates the value against its `dataType` (TEXT " +
            "non-empty; DIGITAL ∈ {0,1}, booleans mapped to 0/1; DOUBLE parseable; COLOR a decimal " +
            "24-bit RGB int; JSON non-empty). Posts with `state=USER_EDIT` so the server's " +
            "filter/trigger pipeline evaluates the snapshot. " +
            "Use `record_snapshot` when you have the UUID and need batch or backfill semantics."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("target") {
                put("type", "string")
                put(
                    "description",
                    "Id (UUID) or display name (`meta.name`) of the target DataPoint. " +
                        "When a plain name is passed (not a UUID), the tool resolves it via `list_nodes` — " +
                        "it must be an exact case-insensitive match of an existing DataPoint's `meta.name`.",
                )
            }
            putJsonObject("value") {
                put(
                    "description",
                    "Value to record. String, number, or boolean. Validated against the DataPoint's " +
                        "`dataType`: TEXT non-empty; DIGITAL ∈ {0,1} (booleans mapped to 0/1); " +
                        "DOUBLE parseable; COLOR a decimal 24-bit RGB int; JSON non-empty.",
                )
            }
            putJsonObject("timestamp") {
                put("type", "integer")
                put("description", "Epoch millis for the snapshot. Defaults to server-received now if omitted.")
            }
        }
        putJsonArray("required") {
            add("target")
            add("value")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val rawTarget = arguments["target"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: target")
        val rawValue = arguments["value"]
            ?: error("Missing required argument: value")
        val timestamp = arguments["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()

        val client = resolve(registry, arguments)

        val existing: JsonObject = when {
            isUuid(rawTarget) -> {
                client.node(rawTarget) as? JsonObject
                    ?: error("DataPoint '$rawTarget' not found on server ${client.serverId}.")
            }
            else -> {
                resolveDataPointByName(client, rawTarget)
            }
        }

        val typeFqn = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (typeFqn != DATA_POINT_FQN) {
            error(
                "Target '$rawTarget' is not a KrillApp.DataPoint (got ${typeFqn ?: "unknown"}). " +
                    "`set_value` only writes to DataPoint nodes — use `update_node` for other types.",
            )
        }

        val resolvedId = existing["id"]?.jsonPrimitive?.contentOrNull ?: rawTarget
        val existingMeta = existing["meta"] as? JsonObject
            ?: error("DataPoint '$resolvedId' has no meta object.")
        val dataType = existingMeta["dataType"]?.jsonPrimitive?.contentOrNull ?: "DOUBLE"

        val stringValue = coerceValue(rawValue, dataType)
            ?: error("Value $rawValue is not valid for dataType=$dataType.")

        val snapshotObj = buildJsonObject {
            put("timestamp", timestamp)
            put("value", stringValue)
        }
        val updatedMeta = JsonObject(existingMeta.toMutableMap().apply { put("snapshot", snapshotObj) })
        val updatedNode = JsonObject(
            existing.toMutableMap().apply {
                put("meta", updatedMeta)
                put("state", JsonPrimitive("USER_EDIT"))
            },
        )
        client.postNode(updatedNode)

        return buildJsonObject {
            put("server", client.serverId)
            put("id", resolvedId)
            put("dataType", dataType)
            put("value", stringValue)
            put("timestamp", timestamp)
            put(
                "note",
                "Posted with state=USER_EDIT; server ingest is asynchronous. Call `read_series` to " +
                    "verify — a ~1.5s delay or a child Filter can cause 0 snapshots on immediate read.",
            )
        }
    }

    private suspend fun resolveDataPointByName(client: KrillClient, name: String): JsonObject {
        val nodes = client.nodes()
        return nodes.firstOrNull { element ->
            val obj = element as? JsonObject ?: return@firstOrNull false
            (obj["meta"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
                ?.equals(name, ignoreCase = true) == true
        } as? JsonObject
            ?: error(
                "No node with name '$name' found on server ${client.serverId}. " +
                    "Pass a UUID or verify the name with `list_nodes`.",
            )
    }

    private fun isUuid(s: String): Boolean = runCatching { UUID.fromString(s) }.isSuccess

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

    companion object {
        private const val DATA_POINT_FQN = "krill.zone.shared.KrillApp.DataPoint"
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
