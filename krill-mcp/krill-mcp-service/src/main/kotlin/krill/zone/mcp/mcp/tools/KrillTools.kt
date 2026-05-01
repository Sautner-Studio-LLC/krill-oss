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
                put(
                    "description",
                    "The node id (bare UUID). Peer-prefixed ids of the form `serverId:nodeId` " +
                        "(returned by `list_nodes type=Peer`) are not supported — krill-mcp v1 does " +
                        "not proxy across servers. The Peer entry in `list_nodes` is itself the full " +
                        "peer-node body; pass the bare UUID after the colon to inspect the local-side " +
                        "Server proxy node.",
                )
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val id = arguments["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: id")
        rejectPeerPrefixedId(id)
        val client = resolve(registry, arguments)
        return client.node(id)
    }

    internal fun rejectPeerPrefixedId(id: String) {
        if (':' !in id) return
        val (serverPart, nodePart) = id.substringBefore(':') to id.substringAfter(':')
        error(
            "Peer-prefixed ids ('$serverPart:$nodePart') are not resolvable by get_node — " +
                "krill-mcp v1 does not proxy node fetches across servers (krill-oss#49). " +
                "The Peer entry returned by list_nodes type=Peer is already the full peer-node body; " +
                "if you need to inspect the local-side Server proxy node for that peer, retry with " +
                "id='$nodePart' alone (returns a KrillApp.Server, not a Peer-typed entry).",
        )
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

private fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    return registry.resolve(selector)
        ?: error("No Krill server matches '$selector' (and no default is registered).")
}
