package krill.zone.mcp.mcp.tools

import krill.zone.mcp.krill.KrillClient
import krill.zone.mcp.krill.KrillNodeType
import krill.zone.mcp.krill.KrillNodeTypes
import krill.zone.mcp.krill.KrillRegistry
import krill.zone.mcp.mcp.Tool
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Ergonomic projections over the swarm-llm-workloads node family
 * (`KrillApp.Swarm.Work`, `KrillApp.Swarm.Batch`, `KrillApp.Server.LLM`
 * advertisements). The krill server ships the dispatch machinery
 * (arbitration, claim windows, leases, fan-out) and `POST /files` /
 * `GET /files/{hash}` blob routes already — these tools add no new server
 * routes, they wrap the existing node-create + invoke + get-node primitives
 * `create_node` / `get_node` already expose (see krill `openspec/changes/
 * swarm-llm-workloads/design.md` §D9).
 *
 * Payload-boundary rule (design D13): file paths in a prompt are inert to the
 * model — there is no filesystem on the inference path. The supported ways to
 * get content to a claimant are (a) `fileRefs` — claim-check pointers into the
 * blob store, pulled lazily by the winner, (b) automation loading content
 * into a node's `meta.snapshot` first, or (c) small inline base64 in `images`.
 */

private const val TYPE_LLM = "krill.zone.shared.KrillApp.Server.LLM"
private const val TYPE_SWARM_WORK = "krill.zone.shared.KrillApp.Swarm.Work"

private val SWARM_WORK_SPEC: KrillNodeType by lazy {
    KrillNodeTypes.byShortName["KrillApp.Swarm.Work"]
        ?: error("KrillApp.Swarm.Work missing from KrillNodeTypes registry — krill-mcp bug.")
}

private val SWARM_BATCH_SPEC: KrillNodeType by lazy {
    KrillNodeTypes.byShortName["KrillApp.Swarm.Batch"]
        ?: error("KrillApp.Swarm.Batch missing from KrillNodeTypes registry — krill-mcp bug.")
}

private val FILE_REF_SCHEMA_DESCRIPTION =
    "Claim-check reference to blob-store content (design D13) — bytes are pulled lazily by whichever node " +
        "claims the work, never inlined on the wire. Obtain a FileRef by uploading bytes to the target " +
        "server's `POST /files` first (not yet wrapped by an MCP tool — call it directly); a hash-only " +
        "shape without a prior upload will fail when the claimant tries to pull it."

/**
 * Creates a `KrillApp.Swarm.Work` node (`SwarmWorkMetaData`, phase OPEN) and
 * invokes it once with EXECUTE (by = itself) — creation alone only persists
 * the node locally; the invoke is what makes the host's processor publish
 * the OPEN state to swarm peers (solicitation). See design D3/D9.
 */
class SubmitSwarmWorkTool(private val registry: KrillRegistry) : Tool {
    override val name = "submit_swarm_work"
    override val description =
        "Advertise one unit of LLM work to the swarm for a remote `Server.LLM` node to claim and execute. " +
            "Creates a KrillApp.Swarm.Work node (phase OPEN) then invokes it EXECUTE (by=self) so the OPEN " +
            "state publishes to swarm peers — a bare create_node would leave it inert. Poll " +
            "`get_swarm_work_status` for OPEN → ASSIGNED → RUNNING → DONE|FAILED. " +
            "File paths in `prompt` are inert (no filesystem on the inference path) — use `fileRefs` for " +
            "larger content or `images` for small inline base64."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port to host this work. Defaults to the first registered server.")
            }
            putJsonObject("prompt") {
                put("type", "string")
                put("description", "The task prompt sent to whichever node claims this work.")
            }
            putJsonObject("images") {
                put("type", "array")
                put("description", "Small inline base64 images (camera-frame scale). Use fileRefs for larger content.")
                putJsonObject("items") { put("type", "string") }
            }
            putJsonObject("fileRefs") {
                put("type", "array")
                put("description", FILE_REF_SCHEMA_DESCRIPTION)
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("hash") { put("type", "string"); put("description", "SHA-256 content address.") }
                        putJsonObject("sizeBytes") { put("type", "integer") }
                        putJsonObject("mime") { put("type", "string") }
                        putJsonObject("host") { put("type", "string"); put("description", "installId of the server hosting the bytes.") }
                        putJsonObject("name") { put("type", "string"); put("description", "Optional display filename.") }
                    }
                    putJsonArray("required") { add("hash"); add("sizeBytes"); add("mime"); add("host") }
                }
            }
            putJsonObject("requiredModel") {
                put("type", "string")
                put("description", "Required advertised model name; omit for any advertised model to be eligible.")
            }
            putJsonObject("minVramClass") {
                put("type", "string")
                put("description", "Minimum advertised VRAM class an eligible claimant must report (e.g. \"8g\", \"24g\", \"80g\", \"apple-m\").")
            }
            putJsonObject("maxCostScore") {
                put("type", "number")
                put("description", "Maximum advertised cost score an eligible claimant may report. Omit for no ceiling.")
            }
            putJsonObject("deadlineAt") {
                put("type", "integer")
                put("description", "Epoch millis after which this work is abandoned. Omit (or 0) to wait forever.")
            }
            putJsonObject("maxPayloadBytes") {
                put("type", "integer")
                put(
                    "description",
                    "Cap on prompt + images + total fileRefs sizeBytes. Enforced at creation AND re-checked at claim-" +
                        "assignment — the server rejects rather than truncating. Must cover the full payload or the " +
                        "work fails at first claim with an explicit error. Defaults to 1 MiB.",
                )
            }
        }
        putJsonArray("required") { add("prompt") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val prompt = arguments["prompt"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: prompt")
        val images = (arguments["images"] as? JsonArray) ?: JsonArray(emptyList())
        val fileRefs = (arguments["fileRefs"] as? JsonArray) ?: JsonArray(emptyList())
        fileRefs.forEachIndexed { index, ref -> validateFileRef(ref, index, "fileRefs") }

        val meta = SWARM_WORK_SPEC.defaultMeta.toMutableMap()
        meta["prompt"] = JsonPrimitive(prompt)
        meta["images"] = images
        meta["fileRefs"] = fileRefs
        arguments["requiredModel"]?.jsonPrimitive?.contentOrNull?.let { meta["requiredModel"] = JsonPrimitive(it) }
        arguments["minVramClass"]?.jsonPrimitive?.contentOrNull?.let { meta["minVramClass"] = JsonPrimitive(it) }
        arguments["maxCostScore"]?.jsonPrimitive?.doubleOrNull?.let { meta["maxCostScore"] = JsonPrimitive(it) }
        arguments["deadlineAt"]?.jsonPrimitive?.longOrNull?.let { meta["deadlineAt"] = JsonPrimitive(it) }
        arguments["maxPayloadBytes"]?.jsonPrimitive?.intOrNull?.let { meta["maxPayloadBytes"] = JsonPrimitive(it) }

        val effectiveMaxPayload = (meta["maxPayloadBytes"] as? JsonPrimitive)?.intOrNull
            ?: (SWARM_WORK_SPEC.defaultMeta["maxPayloadBytes"] as? JsonPrimitive)?.intOrNull
            ?: 1_048_576
        val estimatedBytes = estimatePayloadBytes(prompt, images, fileRefs)
        if (estimatedBytes > effectiveMaxPayload) {
            error(
                "Estimated payload ($estimatedBytes bytes: prompt + images + fileRefs sizeBytes) exceeds " +
                    "maxPayloadBytes ($effectiveMaxPayload). Raise maxPayloadBytes or shrink the payload — " +
                    "file paths in prompts are inert, use fileRefs for large content.",
            )
        }

        val client = resolve(registry, arguments)
        val newId = UUID.randomUUID().toString()
        val node = buildJsonObject {
            put("id", newId)
            put("parent", client.serverId)
            put("host", client.serverId)
            putJsonObject("type") { put("type", SWARM_WORK_SPEC.typeFqn) }
            put("state", "CREATE_OR_OVERWRITE")
            put("meta", JsonObject(meta))
            put("timestamp", 0L)
        }
        client.postNode(node)

        val self = buildJsonObject { put("nodeId", newId); put("hostId", client.serverId) }
        client.invokeNode(newId, self, "EXECUTE")

        return buildJsonObject {
            put("server", client.serverId)
            put("workNodeId", newId)
            put("phase", "OPEN")
            put("maxPayloadBytes", effectiveMaxPayload)
            put(
                "note",
                "Invoked EXECUTE (by=self) to publish OPEN to swarm peers. Poll get_swarm_work_status until " +
                    "phase reaches DONE or FAILED.",
            )
        }
    }
}

/**
 * Creates a `KrillApp.Swarm.Batch` node and invokes it EXECUTE (by = itself)
 * to trigger server-side fan-out into one child `Swarm.Work` per item. The
 * server caps children at `maxChildren` with an explicit error — never
 * silent truncation — so this tool pre-checks the same bound client-side for
 * a fast, clear failure.
 */
class SubmitSwarmBatchTool(private val registry: KrillRegistry) : Tool {
    override val name = "submit_swarm_batch"
    override val description =
        "Dispatch a batch of related LLM work items, sharing one set of eligibility requirements, as child " +
            "KrillApp.Swarm.Work nodes — one auction per item. Creates a KrillApp.Swarm.Batch node then " +
            "invokes it EXECUTE (by=self) to trigger the server-side fan-out; a bare create_node would leave " +
            "it inert. The server caps children at `maxChildren` with an explicit error, never truncation — " +
            "split oversized batches into multiple calls. Poll individual child ids with " +
            "`get_swarm_work_status`, or this node's own meta.completedCount/meta.failedCount for aggregate " +
            "progress (via get_node)."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port to host this batch. Defaults to the first registered server.")
            }
            putJsonObject("items") {
                put("type", "array")
                put("description", "Non-empty array of work items — one child Swarm.Work node is dispatched per item.")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("prompt") { put("type", "string") }
                        putJsonObject("images") {
                            put("type", "array")
                            putJsonObject("items") { put("type", "string") }
                        }
                        putJsonObject("fileRefs") {
                            put("type", "array")
                            put("description", FILE_REF_SCHEMA_DESCRIPTION)
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("hash") { put("type", "string") }
                                    putJsonObject("sizeBytes") { put("type", "integer") }
                                    putJsonObject("mime") { put("type", "string") }
                                    putJsonObject("host") { put("type", "string") }
                                    putJsonObject("name") { put("type", "string") }
                                }
                                putJsonArray("required") { add("hash"); add("sizeBytes"); add("mime"); add("host") }
                            }
                        }
                    }
                    putJsonArray("required") { add("prompt") }
                }
            }
            putJsonObject("requiredModel") {
                put("type", "string")
                put("description", "Required advertised model shared by every item in this batch; omit for any advertised model.")
            }
            putJsonObject("minVramClass") { put("type", "string") }
            putJsonObject("maxCostScore") { put("type", "number") }
            putJsonObject("deadlineAt") {
                put("type", "integer")
                put("description", "Epoch millis after which unclaimed items are abandoned. Omit (or 0) to wait forever.")
            }
            putJsonObject("maxChildren") {
                put("type", "integer")
                put("description", "Cap on child Swarm.Work nodes this batch may spawn. Defaults to the server's default cap.")
            }
        }
        putJsonArray("required") { add("items") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val itemsArg = arguments["items"] as? JsonArray
        if (itemsArg.isNullOrEmpty()) {
            error("Missing required argument: items (non-empty array of {prompt, images?, fileRefs?}).")
        }

        val items = itemsArg.mapIndexed { index, element ->
            val obj = element as? JsonObject ?: error("items[$index] must be an object {prompt, images?, fileRefs?}.")
            val prompt = obj["prompt"]?.jsonPrimitive?.contentOrNull
                ?: error("items[$index].prompt is required.")
            val images = obj["images"] as? JsonArray ?: JsonArray(emptyList())
            val fileRefs = obj["fileRefs"] as? JsonArray ?: JsonArray(emptyList())
            fileRefs.forEachIndexed { fi, ref -> validateFileRef(ref, fi, "items[$index].fileRefs") }
            buildJsonObject {
                put("prompt", prompt)
                put("images", images)
                put("fileRefs", fileRefs)
            }
        }

        val defaultMaxChildren = (SWARM_BATCH_SPEC.defaultMeta["maxChildren"] as? JsonPrimitive)?.intOrNull ?: 16
        val maxChildren = arguments["maxChildren"]?.jsonPrimitive?.intOrNull ?: defaultMaxChildren
        if (items.size > maxChildren) {
            error(
                "items.size (${items.size}) exceeds maxChildren ($maxChildren) — the server rejects rather than " +
                    "truncating. Split into multiple submit_swarm_batch calls, or raise maxChildren.",
            )
        }

        val meta = SWARM_BATCH_SPEC.defaultMeta.toMutableMap()
        meta["items"] = JsonArray(items)
        meta["maxChildren"] = JsonPrimitive(maxChildren)
        arguments["requiredModel"]?.jsonPrimitive?.contentOrNull?.let { meta["requiredModel"] = JsonPrimitive(it) }
        arguments["minVramClass"]?.jsonPrimitive?.contentOrNull?.let { meta["minVramClass"] = JsonPrimitive(it) }
        arguments["maxCostScore"]?.jsonPrimitive?.doubleOrNull?.let { meta["maxCostScore"] = JsonPrimitive(it) }
        arguments["deadlineAt"]?.jsonPrimitive?.longOrNull?.let { meta["deadlineAt"] = JsonPrimitive(it) }

        val client = resolve(registry, arguments)
        val newId = UUID.randomUUID().toString()
        val node = buildJsonObject {
            put("id", newId)
            put("parent", client.serverId)
            put("host", client.serverId)
            putJsonObject("type") { put("type", SWARM_BATCH_SPEC.typeFqn) }
            put("state", "CREATE_OR_OVERWRITE")
            put("meta", JsonObject(meta))
            put("timestamp", 0L)
        }
        client.postNode(node)

        val self = buildJsonObject { put("nodeId", newId); put("hostId", client.serverId) }
        client.invokeNode(newId, self, "EXECUTE")

        return buildJsonObject {
            put("server", client.serverId)
            put("batchNodeId", newId)
            put("itemCount", items.size)
            put("maxChildren", maxChildren)
            put(
                "note",
                "Invoked EXECUTE (by=self) to trigger server-side fan-out into ${items.size} child Swarm.Work " +
                    "node(s). Poll child ids with get_swarm_work_status, or this node with get_node for " +
                    "meta.completedCount/meta.failedCount.",
            )
        }
    }
}

/**
 * Projection over `list_nodes`, spanning every server this krill-mcp
 * instance can reach: every `KrillApp.Server.LLM` node with
 * `meta.swarmEnabled=true`. Nodes with `swarmEnabled=false` (the default)
 * are excluded — they never claim swarm work.
 */
class GetSwarmFleetTool(private val registry: KrillRegistry) : Tool {
    override val name = "get_swarm_fleet"
    override val description =
        "List every KrillApp.Server.LLM node advertising to the swarm (meta.swarmEnabled=true) across every " +
            "Krill server this krill-mcp instance can reach — one call spans the whole reachable swarm, not " +
            "just one server. Nodes with swarmEnabled=false (the default) are excluded."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val fleet = mutableListOf<JsonObject>()
        for (client in registry.all()) {
            val nodes = runCatching { client.nodes() }.getOrNull() ?: continue
            for (element in nodes) {
                val obj = element as? JsonObject ?: continue
                val typeFqn = obj["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                if (typeFqn != TYPE_LLM) continue
                val meta = obj["meta"] as? JsonObject ?: continue
                val swarmEnabled = meta["swarmEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
                if (!swarmEnabled) continue

                val model = meta["model"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val advertisedModels = (meta["advertisedModels"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.ifEmpty { listOf(model) }
                    ?: listOf(model)

                fleet += buildJsonObject {
                    put("nodeId", obj["id"] ?: JsonNull)
                    put("host", client.serverId)
                    putJsonArray("models") { advertisedModels.forEach { add(JsonPrimitive(it)) } }
                    put("vramClass", meta["vramClass"] ?: JsonPrimitive(""))
                    put("costScore", meta["costScore"] ?: JsonPrimitive(-1.0))
                    put("advertisedAt", meta["advertisedAt"] ?: JsonPrimitive(0L))
                    put("swarmEnabled", true)
                }
            }
        }
        return buildJsonObject {
            put("count", fleet.size)
            putJsonArray("fleet") { fleet.forEach { add(it) } }
        }
    }
}

/**
 * Projection over `get_node` for a `KrillApp.Swarm.Work` node: phase,
 * assignee, attempts, error, result. `KrillApp.Swarm.Batch` progress is not
 * covered here — it has no `phase` field, only aggregate
 * `completedCount`/`failedCount`; read those with `get_node`.
 */
class GetSwarmWorkStatusTool(private val registry: KrillRegistry) : Tool {
    override val name = "get_swarm_work_status"
    override val description =
        "Read the dispatch status of a KrillApp.Swarm.Work node: phase (OPEN | ASSIGNED | RUNNING | DONE | " +
            "FAILED), assignee, attempts, error, result. For KrillApp.Swarm.Batch aggregate progress, call " +
            "get_node directly and read meta.completedCount / meta.failedCount."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("nodeId") {
                put("type", "string")
                put("description", "Id of the KrillApp.Swarm.Work node.")
            }
        }
        putJsonArray("required") { add("nodeId") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val nodeId = arguments["nodeId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: nodeId")

        val client = resolve(registry, arguments)
        val node = client.node(nodeId) as? JsonObject
            ?: error("Node $nodeId not found on server ${client.serverId}.")
        val typeFqn = node["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (typeFqn != TYPE_SWARM_WORK) {
            error(
                "Node $nodeId is not a KrillApp.Swarm.Work node (got ${typeFqn ?: "unknown"}). " +
                    "get_swarm_work_status only reads Work nodes — for Batch aggregate progress call get_node " +
                    "and read meta.completedCount / meta.failedCount.",
            )
        }
        val meta = node["meta"] as? JsonObject ?: error("Node $nodeId has no meta object.")

        return buildJsonObject {
            put("server", client.serverId)
            put("nodeId", nodeId)
            put("phase", meta["phase"] ?: JsonPrimitive("OPEN"))
            put("assignee", meta["assignee"] ?: JsonNull)
            put("attempts", meta["attempts"] ?: JsonPrimitive(0))
            put("maxAttempts", meta["maxAttempts"] ?: JsonPrimitive(3))
            put("error", meta["error"] ?: JsonPrimitive(""))
            put("result", meta["result"] ?: JsonNull)
        }
    }
}

/** Validate a caller-supplied FileRef JsonElement, failing fast with a field-level message. */
private fun validateFileRef(element: JsonElement, index: Int, label: String) {
    val obj = element as? JsonObject
        ?: error("$label[$index] must be an object {hash, sizeBytes, mime, host, name?}.")
    obj["hash"]?.jsonPrimitive?.contentOrNull
        ?: error("$label[$index].hash is required (SHA-256 content address).")
    obj["sizeBytes"]?.jsonPrimitive?.longOrNull
        ?: error("$label[$index].sizeBytes is required.")
    obj["mime"]?.jsonPrimitive?.contentOrNull
        ?: error("$label[$index].mime is required.")
    obj["host"]?.jsonPrimitive?.contentOrNull
        ?: error("$label[$index].host is required (installId of the server hosting the bytes).")
}

/** Sum of prompt bytes + inline image string lengths + declared fileRefs sizeBytes. */
private fun estimatePayloadBytes(prompt: String, images: JsonArray, fileRefs: JsonArray): Long {
    var total = prompt.toByteArray(Charsets.UTF_8).size.toLong()
    images.forEach { total += (it.jsonPrimitive.contentOrNull?.length ?: 0).toLong() }
    fileRefs.forEach { ref ->
        total += (ref as? JsonObject)?.get("sizeBytes")?.jsonPrimitive?.longOrNull ?: 0L
    }
    return total
}

private fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    return registry.resolve(selector)
        ?: error("No Krill server matches '$selector' (and no default is registered). Try reseed_servers — the registry may have missed the initial probe.")
}
