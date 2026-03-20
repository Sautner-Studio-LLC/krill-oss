package krill.zone.service

import com.google.protobuf.Empty
import io.grpc.Server
import krill.zone.Pi4jContextManager
import krill.zone.pi4j.proto.*
import org.slf4j.LoggerFactory

/**
 * Liveness, inventory, and graceful shutdown.
 *
 * A reference to the [Server] is injected after construction so that
 * the [shutdown] RPC can initiate a clean teardown.
 */
class SystemServiceImpl(
    private val ctx: Pi4jContextManager = Pi4jContextManager
) : SystemServiceGrpcKt.SystemServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(SystemServiceImpl::class.java)

    /** Injected by Main after the server is started. */
    var server: Server? = null

    // ── Ping ──────────────────────────────────────────────────────────────────

    override suspend fun ping(request: Empty): PingResponse = pingResponse {
        timestampMillis = System.currentTimeMillis()
        version         = serviceVersion()
    }

    // ── GetInfo ───────────────────────────────────────────────────────────────

    override suspend fun getInfo(request: Empty): SystemInfoResponse {
        val context = ctx.context
        return systemInfoResponse {
            serviceVersion = krill.zone.Version.SERVICE
            pi4JVersion    = context.versionString()

            platforms += context.platforms().all().values.map { it.id() }
            providers += context.providers().all().values.map { it.id() }

            properties["java.version"]  = System.getProperty("java.version", "unknown")
            properties["os.name"]       = System.getProperty("os.name", "unknown")
            properties["os.arch"]       = System.getProperty("os.arch", "unknown")
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    override suspend fun shutdown(request: Empty): Empty {
        log.info("Shutdown requested via gRPC")
        // Kick off shutdown on a separate thread so the RPC can complete first
        Thread {
            Thread.sleep(200)
            server?.shutdown()
            ctx.shutdown()
        }.also { it.isDaemon = true }.start()
        return Empty.getDefaultInstance()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun serviceVersion() = krill.zone.Version.SERVICE

    private fun com.pi4j.context.Context.versionString(): String =
        runCatching { this.javaClass.`package`.implementationVersion ?: "4.x" }
            .getOrDefault("unknown")
}
