package krill.zone.mcp.mcp.tools

import krill.zone.mcp.krill.KrillClient
import krill.zone.mcp.krill.KrillRegistry
import krill.zone.mcp.mcp.Tool
import kotlinx.serialization.json.*

/**
 * v1 tool surface: read-only passthrough proxies over the Krill REST API.
 *
 * Each tool takes an optional `server` argument — the Krill server id, host,
 * or host:port — falling back to the first registered server when omitted.
 */
class ListServersTool(private val registry: KrillRegistry) : Tool {
    override val name = "list_servers"
    override val description = "List all Krill servers this krill-mcp instance can reach."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
        putJsonArray("required") {}
    }

    override suspend fun execute(arguments: JsonObject): JsonElement = buildJsonObject {
        putJsonArray("servers") {
            registry.all().forEach { c ->
                addJsonObject {
                    put("id", c.serverId)
                    put("baseUrl", c.baseUrl)
                }
            }
        }
    }
}

class ListNodesTool(private val registry: KrillRegistry) : Tool {
    override val name = "list_nodes"
    override val description =
        "List nodes on a Krill server. Optionally filter client-side by `type` substring (e.g. \"Pin\", \"DataPoint\")."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("type") {
                put("type", "string")
                put("description", "Case-insensitive substring match on the node type. Optional.")
            }
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val typeFilter = arguments["type"]?.jsonPrimitive?.contentOrNull?.lowercase()
        val raw = client.nodes()
        val filtered = if (typeFilter == null) raw else JsonArray(raw.filter {
            val nodeType = (it as? JsonObject)?.get("type")?.toString()?.lowercase() ?: ""
            typeFilter in nodeType
        })
        // Surface `meta.name` as a top-level `displayName` so agents scanning a
        // nodes array can identify a node by its human name without having to
        // dig through the per-type meta shape. Projects in particular were
        // indistinguishable without this — every Project's type label is
        // "Project", so "the Water Quality project" had no footprint in the
        // response.
        val enriched = JsonArray(
            filtered.map { element ->
                val obj = element as? JsonObject ?: return@map element
                val displayName = (obj["meta"] as? JsonObject)
                    ?.get("name")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    .orEmpty()
                JsonObject(obj.toMutableMap().apply { put("displayName", JsonPrimitive(displayName)) })
            },
        )
        return buildJsonObject {
            put("server", client.serverId)
            put("count", enriched.size)
            put("nodes", enriched)
        }
    }
}

class GetNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "get_node"
    override val description = "Fetch a single node's full state by id."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("id") {
                put("type", "string")
                put("description", "The node id (UUID).")
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")
        return client.node(id)
    }
}

class ReadSeriesTool(private val registry: KrillRegistry) : Tool {
    override val name = "read_series"
    override val description =
        "Read time-series data for a DataPoint node. Defaults to the last hour; pass ms-since-epoch bounds to override."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("id") {
                put("type", "string")
                put("description", "DataPoint node id.")
            }
            putJsonObject("startMs") {
                put("type", "integer")
                put("description", "Range start in ms since epoch. Default: one hour before now.")
            }
            putJsonObject("endMs") {
                put("type", "integer")
                put("description", "Range end in ms since epoch. Default: now.")
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")
        val now = System.currentTimeMillis()
        val startMs = arguments["startMs"]?.jsonPrimitive?.longOrNull ?: (now - 60 * 60 * 1000)
        val endMs = arguments["endMs"]?.jsonPrimitive?.longOrNull ?: now
        val series = client.series(id, startMs, endMs)
        return buildJsonObject {
            put("server", client.serverId)
            put("nodeId", id)
            put("startMs", startMs)
            put("endMs", endMs)
            put("count", series.size)
            put("series", series)
        }
    }
}

/**
 * Manual fire-once primitive. Mirrors the Compose client's `nodeManager.execute(node)`
 * call (see `ClientNodeManager.execute` in the upstream Krill repo) — which over the
 * wire is just `POST /node/{id}` with the existing body and `state="EXECUTED"`.
 *
 * The Krill server's `update()` runs `node.type.emit(node)` on every upsert; the
 * per-type processor (`ServerExecutorProcessor`, `LogicGateProcessor`, etc.) then
 * reacts to `state == NodeState.EXECUTED` and runs the action. So this tool
 * doesn't need a new server endpoint — it composes existing ones.
 *
 * Type filter: rejects pure-container / infrastructure types where firing is a
 * no-op or semantically wrong (`KrillApp.Server`, `KrillApp.Client`, peer links,
 * backup tasks). Anything else is allowed; the per-type processor decides what
 * EXECUTED means for it (DataPoint propagates downstream, Trigger fires its
 * children, Executor performs its action).
 */
class ExecuteNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "execute_node"
    override val description =
        "Manually fire a Trigger or Executor node once — equivalent to tapping the manual-execute button " +
            "in the Krill app. Use this for `KrillApp.Executor.LogicGate`, `KrillApp.Executor.Lambda`, " +
            "`KrillApp.Trigger.Button`, etc., when you want to run the action immediately and don't want " +
            "to synthesize an upstream DataPoint snapshot just to drive the chain. Posts the node body " +
            "back with `state=EXECUTED`; the server's per-type processor reacts and runs the action " +
            "asynchronously. Returns the post-fire node so you can verify without a separate `get_node`. " +
            "Rejects pure-container / infrastructure types (`KrillApp.Server`, `KrillApp.Client`, " +
            "peer / backup nodes) where manual fire is meaningless."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("id") {
                put("type", "string")
                put("description", "The id of the node to fire (UUID).")
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")

        val existing = runCatching { client.node(id) as? JsonObject }.getOrNull()
            ?: error("Node $id not found on server ${client.serverId}.")
        val typeFqn = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
            ?: error("Node $id has no type discriminator.")
        if (!isFirable(typeFqn)) {
            error(
                "Node type $typeFqn does not support manual execution. Pure containers (Server, Client) and " +
                    "infrastructure nodes (Peer, Backup) cannot be fired — mutate the appropriate child instead.",
            )
        }

        val priorState = existing["state"]?.jsonPrimitive?.contentOrNull
        val now = System.currentTimeMillis()
        val updatedNode = JsonObject(
            existing.toMutableMap().apply {
                put("state", JsonPrimitive("EXECUTED"))
                put("timestamp", JsonPrimitive(now))
            },
        )

        runCatching { client.postNode(updatedNode) }.onFailure { ex ->
            error("execution failed: ${ex.message ?: ex::class.simpleName ?: "unknown error"}")
        }

        // Round-trip read so the caller sees what the server actually persisted —
        // matches the documented `create_node` / `update_diagram` discipline.
        val postFire = runCatching { client.node(id) }.getOrNull()

        return buildJsonObject {
            put("server", client.serverId)
            put("nodeId", id)
            put("type", typeFqn)
            if (priorState != null) put("priorState", priorState)
            put("requestedState", "EXECUTED")
            put("firedAt", now)
            if (postFire != null) put("node", postFire)
            put(
                "note",
                "POST returns 202; the per-type processor runs asynchronously. The node body above is the " +
                    "post-fire GET — for actions with downstream effects (a LogicGate flipping a Pin, an " +
                    "Executor recording a snapshot), allow ~1s before reading the affected target.",
            )
        }
    }

    companion object {
        private val NON_FIRABLE_TYPE_FQNS = setOf(
            "krill.zone.shared.KrillApp.Server",
            "krill.zone.shared.KrillApp.Client",
            "krill.zone.shared.KrillApp.Client.About",
            "krill.zone.shared.KrillApp.Server.Peer",
            "krill.zone.shared.KrillApp.Server.Backup",
        )

        /**
         * Whether a node of the given fully-qualified `KrillApp.*` type can be
         * manually fired. Block-list, not allow-list — the upstream catalog
         * grows; defaulting unknown types to "firable" keeps the tool useful as
         * new node types ship without coordinated MCP releases.
         */
        fun isFirable(typeFqn: String): Boolean = typeFqn !in NON_FIRABLE_TYPE_FQNS
    }
}

class ServerHealthTool(private val registry: KrillRegistry) : Tool {
    override val name = "server_health"
    override val description = "Return the /health payload of a Krill server (server info + peer list)."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        return client.health()
    }
}

private fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    return registry.resolve(selector)
        ?: error("No Krill server matches '$selector' (and no default is registered).")
}
