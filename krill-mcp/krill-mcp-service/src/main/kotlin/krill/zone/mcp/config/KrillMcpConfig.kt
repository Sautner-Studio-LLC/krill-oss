package krill.zone.mcp.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

/**
 * On-disk config for krill-mcp. Lives at /etc/krill-mcp/config.json.
 *
 * Example:
 *
 *     {
 *       "listenPort": 50052,
 *       "pinDerivedKeyPath": "/etc/krill-mcp/credentials/pin_derived_key",
 *       "seeds": [
 *         { "host": "krill.local", "port": 8442 }
 *       ]
 *     }
 */
@Serializable
data class KrillMcpConfig(
    val listenPort: Int = 50052,
    val pinDerivedKeyPath: String = "/etc/krill-mcp/credentials/pin_derived_key",
    val seeds: List<KrillSeed> = emptyList(),
    /** Seconds between peer-discovery refreshes from each seed's /nodes endpoint. */
    val discoveryRefreshSeconds: Long = 300,
)

@Serializable
data class KrillSeed(
    val host: String,
    val port: Int = 8442,
    /** Override the bearer token for this seed only (rare — normally the cluster PIN covers everyone). */
    val bearerToken: String? = null,
) {
    fun baseUrl(): String = "https://$host:$port"
}

object KrillMcpConfigLoader {
    private val log = LoggerFactory.getLogger(KrillMcpConfigLoader::class.java)

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun load(path: String = DEFAULT_PATH): KrillMcpConfig {
        val file = File(path)
        if (!file.exists()) {
            log.warn("No config at {} — using defaults (no seeds configured).", path)
            return KrillMcpConfig()
        }
        return json.decodeFromString(KrillMcpConfig.serializer(), file.readText())
    }

    const val DEFAULT_PATH = "/etc/krill-mcp/config.json"
}
