package krill.zone.mcp.mcp.tools

import krill.zone.mcp.krill.KrillClient
import krill.zone.mcp.krill.KrillRegistry
import krill.zone.mcp.mcp.Tool
import kotlinx.serialization.json.*

/**
 * Resolve a free-text query (e.g. "the Vivarium mister") to one or more
 * concrete (serverId, nodeId) candidates. Iterates every registered Krill
 * server (or a single server when `scope: "server"`), scores each node
 * against the query, and returns the top-k ranked candidates.
 *
 * Scoring is biased toward recall: the caller decides whether a single top
 * match is good enough or to ask the user for confirmation when scores are
 * close. The pure ranking helpers ([rank], [scoreCandidate]) are exposed
 * `internal` so tests can drive them with synthetic node data without
 * standing up a real Krill server.
 */
class FindNodeTool(private val registry: KrillRegistry) : Tool {
    override val name = "find_node"
    override val description =
        "Resolve a free-text phrase to one or more (serverId, nodeId) candidates by name + parent path " +
            "(plus type as a tiebreaker). Iterates every registered server by default; pass `scope: " +
            "\"server\"` to restrict to one. Use this BEFORE `get_node` when the user names a node by " +
            "its display name or surrounding context (\"the Vivarium mister\", \"living room lamp\") — " +
            "it replaces the multi-round-trip ritual of `list_nodes` → substring scan → `get_node`. " +
            "Returns up to `limit` ranked candidates with normalized scores in [0, 1]; an empty array " +
            "(not an error) when nothing matches."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put(
                    "description",
                    "Free-text phrase from the user — typically a node's display name or the surrounding " +
                        "context (e.g. \"vivarium mister\", \"basement humidity\").",
                )
            }
            putJsonObject("type") {
                put("type", "string")
                put(
                    "description",
                    "Optional case-insensitive type filter — substring match on the KrillApp type FQN. " +
                        "Pass either the short name (\"Pin\", \"LogicGate\") or any unique substring.",
                )
            }
            putJsonObject("scope") {
                put("type", "string")
                put(
                    "description",
                    "\"swarm\" (default) iterates every registered server. \"server\" restricts to one " +
                        "— pair with `server` to pick which.",
                )
                putJsonArray("enum") { add("swarm"); add("server") }
            }
            putJsonObject("server") {
                put("type", "string")
                put(
                    "description",
                    "Krill server id, host, or host:port. Used only when `scope: \"server\"`. Defaults " +
                        "to the first registered server.",
                )
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Max candidates to return. Default 5, capped at 25.")
            }
        }
        putJsonArray("required") { add("query") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: query")
        val typeFilter = arguments["type"]?.jsonPrimitive?.contentOrNull
        val scope = arguments["scope"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "swarm"
        val limit = (arguments["limit"]?.jsonPrimitive?.intOrNull ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)

        val clients: List<KrillClient> = when (scope) {
            "server" -> listOf(resolveServer(registry, arguments))
            "swarm" -> registry.all().ifEmpty { listOfNotNull(registry.resolve(null)) }
            else -> error("Unknown scope '$scope'. Expected \"swarm\" or \"server\".")
        }

        if (clients.isEmpty()) {
            return buildJsonObject {
                put("query", query)
                put("scope", scope)
                put("count", 0)
                putJsonArray("candidates") {}
                put("note", "No Krill servers registered. Try `reseed_servers` first.")
            }
        }

        val byServer: Map<String, JsonArray> = clients.associate { client ->
            client.serverId to runCatching { client.nodes() }.getOrElse { JsonArray(emptyList()) }
        }

        val candidates = rank(query, typeFilter, byServer, limit)

        return buildJsonObject {
            put("query", query)
            put("scope", scope)
            put("count", candidates.size)
            putJsonArray("candidates") {
                candidates.forEach { c ->
                    addJsonObject {
                        put("serverId", c.serverId)
                        put("nodeId", c.nodeId)
                        put("type", c.type)
                        put("displayName", c.displayName)
                        put("path", c.path)
                        put("score", c.score)
                        put("summary", c.summary)
                    }
                }
            }
        }
    }

    /**
     * Rank every node in [byServer] against [query], optionally restricted to
     * nodes whose type FQN contains [typeFilter] (case-insensitive). Returns
     * the top [limit] candidates by descending score. Empty/whitespace
     * queries return an empty list — the agent should treat that as "ask the
     * user" rather than firing on nothing.
     */
    internal fun rank(
        query: String,
        typeFilter: String?,
        byServer: Map<String, JsonArray>,
        limit: Int,
    ): List<Candidate> {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val fullQuery = tokens.joinToString(" ")
        val typeFilterLower = typeFilter?.lowercase()

        val all = mutableListOf<Candidate>()
        byServer.forEach { (serverId, nodes) ->
            val byId: Map<String, JsonObject> = nodes
                .mapNotNull { it as? JsonObject }
                .mapNotNull { obj -> obj.id()?.let { it to obj } }
                .toMap()

            byId.values.forEach { node ->
                val typeFqn = node.typeFqn() ?: return@forEach
                if (typeFilterLower != null && !typeFqn.lowercase().contains(typeFilterLower)) return@forEach
                if (typeFqn == TYPE_SERVER) return@forEach // don't surface server nodes themselves

                val displayName = node.displayName()
                val parentPath = parentPathOf(node, byId)
                val typeShortName = typeFqn.substringAfter("krill.zone.shared.")

                val score = scoreCandidate(
                    queryTokens = tokens,
                    fullQuery = fullQuery,
                    name = displayName,
                    parentPath = parentPath,
                    typeShortName = typeShortName,
                )
                if (score <= 0.0) return@forEach

                val nodeId = node.id() ?: return@forEach
                all += Candidate(
                    serverId = serverId,
                    nodeId = nodeId,
                    type = typeShortName,
                    displayName = displayName,
                    path = if (parentPath.isEmpty()) displayName else "$parentPath > $displayName",
                    score = score,
                    summary = buildSummary(typeShortName, parentPath, displayName),
                )
            }
        }

        return all
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.displayName })
            .take(limit)
    }

    /**
     * Score a single candidate. Per query token: name match weighs more than
     * parent-path match, which weighs more than type match. A bonus is added
     * when the full joined query is a substring of the candidate name.
     * Result is normalized into [0, 1] against the per-token name-match max
     * so a perfect full match scores ~1.0.
     */
    internal fun scoreCandidate(
        queryTokens: List<String>,
        fullQuery: String,
        name: String,
        parentPath: String,
        typeShortName: String,
    ): Double {
        val nameLower = name.lowercase()
        val pathLower = parentPath.lowercase()
        val typeLower = typeShortName.lowercase()

        var raw = 0.0
        for (token in queryTokens) {
            when {
                token in nameLower -> raw += W_NAME
                token in pathLower -> raw += W_PATH
                token in typeLower -> raw += W_TYPE
            }
        }
        if (fullQuery.isNotEmpty() && fullQuery in nameLower) raw += W_FULL_QUERY_BONUS

        val maxRaw = W_NAME * queryTokens.size + W_FULL_QUERY_BONUS
        return (raw / maxRaw).coerceIn(0.0, 1.0)
    }

    data class Candidate(
        val serverId: String,
        val nodeId: String,
        val type: String,
        val displayName: String,
        val path: String,
        val score: Double,
        val summary: String,
    )

    private fun parentPathOf(node: JsonObject, byId: Map<String, JsonObject>): String {
        val segments = mutableListOf<String>()
        var current: JsonObject? = node.parent()?.let { byId[it] }
        var hops = 0
        // Cap the walk so a malformed parent loop can't spin us forever.
        while (current != null && hops < MAX_PARENT_HOPS) {
            val typeFqn = current.typeFqn()
            if (typeFqn == TYPE_SERVER) break
            val name = current.displayName()
            if (name.isNotBlank()) segments += name
            val nextParentId = current.parent()
            if (nextParentId == null || nextParentId == current.id()) break
            current = byId[nextParentId]
            hops++
        }
        return segments.asReversed().joinToString(" > ")
    }

    private fun buildSummary(typeShortName: String, parentPath: String, displayName: String): String {
        val parentTail = parentPath.substringAfterLast(" > ", missingDelimiterValue = parentPath)
        return when {
            displayName.isBlank() && parentTail.isBlank() -> typeShortName
            displayName.isBlank() -> "$typeShortName under $parentTail"
            parentTail.isBlank() -> typeShortName
            else -> "$typeShortName under $parentTail"
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 25
        const val MAX_PARENT_HOPS = 16

        const val W_NAME = 3.0
        const val W_PATH = 1.5
        const val W_TYPE = 0.5
        const val W_FULL_QUERY_BONUS = 2.0

        const val TYPE_SERVER = "krill.zone.shared.KrillApp.Server"
    }
}

private fun JsonObject.id(): String? = get("id")?.jsonPrimitive?.contentOrNull
private fun JsonObject.parent(): String? = get("parent")?.jsonPrimitive?.contentOrNull
private fun JsonObject.typeFqn(): String? =
    (get("type") as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
private fun JsonObject.displayName(): String =
    (get("meta") as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()

private fun resolveServer(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    return registry.resolve(selector)
        ?: error(
            "No Krill server matches '$selector' (and no default is registered). Try `reseed_servers` — " +
                "the registry may have missed the initial probe.",
        )
}
