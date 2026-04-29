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

private suspend fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    registry.resolve(selector)?.let { return it }
    if (selector != null && KrillRegistry.looksLikeHost(selector)) {
        error("host unreachable: $selector — krill-mcp tried to lazy-register but /health did not respond (check DNS, the port in the selector (default 8442), and the swarm bearer).")
    }
    error("No Krill server matches '$selector' (and no default is registered).")
}
