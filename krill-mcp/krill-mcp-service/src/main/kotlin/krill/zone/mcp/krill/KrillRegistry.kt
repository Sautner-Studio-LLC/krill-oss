package krill.zone.mcp.krill

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.config.KrillSeed
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks known Krill servers. v1 is static: one KrillClient per seed in config.
 *
 * The seed's server id is resolved by hitting /health once on startup — this
 * double-checks that the bearer token works and gives us a stable identifier
 * for MCP tool calls ("server" parameter accepts either id or host).
 *
 * Peer auto-discovery from ServerMetaData is deliberately out of scope for v1.
 */
class KrillRegistry(
    private val config: KrillMcpConfig,
    private val pin: PinProvider,
) {
    private val log = LoggerFactory.getLogger(KrillRegistry::class.java)

    private val byId = ConcurrentHashMap<String, KrillClient>()
    private val byHost = ConcurrentHashMap<String, KrillClient>()

    suspend fun bootstrap() {
        if (config.seeds.isEmpty()) {
            log.warn("No seeds configured in /etc/krill-mcp/config.json — MCP tools will have no Krill server to call.")
            return
        }
        for (seed in config.seeds) {
            val client = probe(seed) ?: continue
            byId[client.serverId] = client
            byHost["${seed.host}:${seed.port}"] = client
            log.info("Registered Krill server: id={} host={}:{}", client.serverId, seed.host, seed.port)
        }
        if (byId.isEmpty()) {
            log.warn("No Krill servers reachable. MCP tools will return no results until a seed comes online.")
        }
    }

    private suspend fun probe(seed: KrillSeed): KrillClient? = coroutineScope {
        val label = "${seed.host}:${seed.port}"
        val tokenSupplier: () -> String? = { seed.bearerToken ?: pin.bearerToken() }
        if (tokenSupplier() == null) {
            log.warn("Seed {} skipped: no PIN-derived bearer token available (check {}).",
                label, "/etc/krill-mcp/credentials/pin_derived_key")
            return@coroutineScope null
        }
        val candidate = KrillClient(
            serverId = "pending",
            baseUrl = seed.baseUrl(),
            bearerToken = tokenSupplier,
        )
        val health = withTimeoutOrNull(10_000) {
            runCatching { candidate.health() }.fold(
                onSuccess = { it },
                onFailure = {
                    log.warn("Seed {} /health failed: {}", label, it.message)
                    null
                },
            )
        } ?: run {
            // withTimeoutOrNull returns null when the block itself returned null (see fold above)
            // OR on actual timeout; either way we logged above unless this was the timeout branch.
            return@coroutineScope null
        }
        val serverId = (health as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
        if (serverId == null) {
            log.warn("Seed {} responded but /health lacks a top-level \"id\" field; cannot register. Payload: {}",
                label, health.toString().take(200))
            return@coroutineScope null
        }
        KrillClient(serverId = serverId, baseUrl = seed.baseUrl(), bearerToken = tokenSupplier)
    }

    fun all(): List<KrillClient> = byId.values.toList()

    /** Resolve "server" tool param — accepts server id, "host", or "host:port". */
    fun resolve(selector: String?): KrillClient? {
        if (selector == null) return byId.values.firstOrNull()
        byId[selector]?.let { return it }
        byHost[selector]?.let { return it }
        // "host" without port — check each host key
        byHost.entries.firstOrNull { it.key.substringBefore(":") == selector }?.let { return it.value }
        return null
    }
}
