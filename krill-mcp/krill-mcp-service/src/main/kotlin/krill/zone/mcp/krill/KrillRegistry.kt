package krill.zone.mcp.krill

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import krill.zone.mcp.config.KrillSeed
import krill.zone.shared.krillapp.server.ServerMetaData
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks known Krill servers. The registry starts from `config.seeds[]` at
 * boot, and grows lazily when a tool call references a host that wasn't in
 * the seed config (see [tryRegisterByHost]).
 *
 * The seed's server id is resolved by hitting /health once on startup — this
 * double-checks that the bearer token works and gives us a stable identifier
 * for MCP tool calls ("server" parameter accepts either id or host).
 *
 * Transitive peer auto-discovery from `/health` is still out of scope: the
 * krill server filters `KrillApp.Server` nodes out of its `/health` payload,
 * so there's nothing to recurse on. Tracked upstream as bsautner/krill#178;
 * once it lands, [bootstrap]/[reseed] can walk the swarm from one seed.
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
        probeAllSeeds(attempts = 5, backoffMs = 2_000)
        if (byId.isEmpty()) {
            log.warn("No Krill servers reachable after bootstrap retries. Call `reseed_servers` (MCP tool) or `sudo systemctl restart krill-mcp` when Krill is healthy.")
        }
    }

    /**
     * Force a re-probe of every configured seed. Replaces registry contents
     * atomically: entries for seeds that newly succeed are (re)registered;
     * entries for seeds that still fail stay as they were (we don't evict a
     * working client on a transient failure).
     *
     * Exposed as the `reseed_servers` MCP tool so agents can recover from the
     * startup seed race without shell access.
     */
    suspend fun reseed(): List<KrillClient> {
        if (config.seeds.isEmpty()) return emptyList()
        probeAllSeeds(attempts = 1, backoffMs = 0)
        return all()
    }

    /**
     * Probe every configured seed with a bounded retry loop. [attempts] is per
     * seed; between failed attempts we wait [backoffMs] ms. Seeds that succeed
     * at any attempt get registered; seeds that exhaust all attempts stay
     * unregistered (bootstrap logs a warning).
     *
     * The retry fixes the common startup race: systemd brings krill-mcp up
     * before the krill server it targets is listening, and a single probe on
     * cold boot finds a socket error or a "server prematurely closed the
     * connection" and gives up.
     */
    private suspend fun probeAllSeeds(attempts: Int, backoffMs: Long) = coroutineScope {
        config.seeds.forEach { seed ->
            val label = "${seed.host}:${seed.port}"
            var client: KrillClient? = null
            for (attempt in 1..attempts) {
                client = probe(seed)
                if (client != null) break
                if (attempt < attempts) {
                    log.info("Seed {} probe attempt {}/{} failed; retrying in {}ms", label, attempt, attempts, backoffMs)
                    delay(backoffMs)
                }
            }
            if (client != null) {
                byId[client.serverId] = client
                byHost[label] = client
                log.info("Registered Krill server: id={} host={}", client.serverId, label)
            }
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
            publicBaseUrl = seed.baseUrl(),
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
        val healthObj = health as? JsonObject ?: run {
            log.warn("Seed {} /health returned non-object payload: {}", label, health.toString().take(200))
            return@coroutineScope null
        }
        val serverId = healthObj["id"]?.jsonPrimitive?.contentOrNull
        if (serverId == null) {
            log.warn("Seed {} responded but /health lacks a top-level \"id\" field; cannot register. Payload: {}",
                label, healthObj.toString().take(200))
            return@coroutineScope null
        }
        val publicBaseUrl = publicBaseUrlFromHealth(healthObj) ?: seed.baseUrl()
        KrillClient(
            serverId = serverId,
            baseUrl = seed.baseUrl(),
            publicBaseUrl = publicBaseUrl,
            bearerToken = tokenSupplier,
        )
    }

    /**
     * Derive a client-resolvable base URL from the `/health` payload so Diagram
     * `source` URLs point somewhere a phone / browser can actually load.
     * Delegates to the SDK's [ServerMetaData.getUrl] for the resolution rules
     * (`.local` suffixing for bare/mDNS hostnames, FQDNs/IPs pass through).
     */
    private fun publicBaseUrlFromHealth(health: JsonObject): String? {
        val metaJson = health["meta"] as? JsonObject ?: return null
        val meta = runCatching {
            healthMetaJson.decodeFromJsonElement(ServerMetaData.serializer(), metaJson)
        }.getOrNull() ?: return null
        if (meta.name.isBlank() || meta.port <= 0) return null
        return meta.getUrl()
    }

    /**
     * Try to dial [selector] (a host or `host:port`) and register it on success.
     * Returns the registered client, or null if the selector doesn't look like a
     * host, the host was unreachable, or `/health` rejected the swarm bearer.
     *
     * The lazy companion to [bootstrap]: an agent can hand a tool a
     * `server: "pi-krill.local"` selector and the swarm picks it up on first
     * use — no shell access on the krill-mcp host, no edits to
     * `/etc/krill-mcp/config.json`, no `systemctl restart`. Closes the gap in
     * bsautner/krill-oss#23 (Part B).
     */
    suspend fun tryRegisterByHost(selector: String): KrillClient? {
        if (!looksLikeHost(selector)) return null
        val (host, port) = parseHostPort(selector)
        val seed = KrillSeed(host = host, port = port)
        val label = "${seed.host}:${seed.port}"
        // Already known under any synonym (id or host:port)? Just hand it
        // back so a follow-up tool call with the same hostname doesn't reprobe.
        byHost[label]?.let { return it }
        val client = probe(seed) ?: return null
        byId[client.serverId] = client
        byHost[label] = client
        log.info("Lazy-registered Krill server: id={} host={}", client.serverId, label)
        return client
    }

    private fun parseHostPort(selector: String): Pair<String, Int> {
        val colonIdx = selector.lastIndexOf(':')
        if (colonIdx < 0) return selector to KrillSeed.DEFAULT_PORT
        val host = selector.substring(0, colonIdx)
        val port = selector.substring(colonIdx + 1).toIntOrNull() ?: KrillSeed.DEFAULT_PORT
        return host to port
    }

    companion object {
        private val healthMetaJson = Json { ignoreUnknownKeys = true }

        /**
         * Does [selector] look like a host (or host:port) we should try to dial?
         *
         * Conservative on purpose: requires a literal `.` (FQDN / `.local` /
         * dotted-quad IP) or `:` (explicit port). Bare names without a dot
         * collide with the UUID space — a UUID like
         * `f47ac10b-58cc-4372-a567-0e02b2c3d479` matches `^[a-z0-9-]+$` too,
         * and probing it would burn the connect timeout for nothing. Real
         * peers in this swarm always carry a `.local` suffix anyway.
         */
        fun looksLikeHost(selector: String?): Boolean {
            if (selector.isNullOrBlank()) return false
            return '.' in selector || ':' in selector
        }
    }

    fun all(): List<KrillClient> = byId.values.toList()

    /**
     * Resolve "server" tool param — accepts server id, "host", or "host:port".
     *
     * Resolution order:
     * 1. Exact match in the registry (id or host:port).
     * 2. If the registry is empty and seeds are configured, re-probe seeds
     *    once (recovers from a startup race without shell access).
     * 3. If the selector looks like a host, dial it and register it
     *    (lazy registration — see [tryRegisterByHost]).
     */
    suspend fun resolve(selector: String?): KrillClient? {
        lookup(selector)?.let { return it }
        // Registry empty or the selector doesn't match — the startup probe
        // likely lost its race. Try once more before giving up so a
        // just-in-time retry can recover the seed without shell access.
        if (byId.isEmpty() && config.seeds.isNotEmpty()) {
            log.info("Registry empty on resolve('{}'); re-probing seeds now.", selector)
            probeAllSeeds(attempts = 1, backoffMs = 0)
            lookup(selector)?.let { return it }
        }
        if (selector != null && looksLikeHost(selector)) {
            return tryRegisterByHost(selector)
        }
        return null
    }

    private fun lookup(selector: String?): KrillClient? {
        if (selector == null) return byId.values.firstOrNull()
        byId[selector]?.let { return it }
        byHost[selector]?.let { return it }
        // "host" without port — check each host key
        byHost.entries.firstOrNull { it.key.substringBefore(":") == selector }?.let { return it.value }
        return null
    }
}
