package krill.zone.service

import com.google.protobuf.*
import com.krillforge.pi4j.proto.*
import com.pi4j.boardinfo.util.*
import io.grpc.*
import krill.zone.*
import org.slf4j.*

/**
 * Liveness, inventory, and graceful shutdown.
 *
 * A reference to the [Server] is injected after construction so that
 * the [shutdown] RPC can initiate a clean teardown.
 */
class DefaultSystemService(
    private val ctx: Pi4jContextManager = Pi4jContextManager
) : SystemServiceGrpcKt.SystemServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(DefaultSystemService::class.java)

    /** Injected by Main after the server is started. */
    var server: Server? = null

    // ── Ping ──────────────────────────────────────────────────────────────────

    override suspend fun ping(request: Empty): PingResponse = pingResponse {
        timestampMillis = System.currentTimeMillis()
        version = serviceVersion()
    }

    // ── GetInfo ───────────────────────────────────────────────────────────────

    override suspend fun getInfo(request: Empty): SystemInfoResponse {
        val context = ctx.context
        return systemInfoResponse {
            serviceVersion = Version.SERVICE
            pi4JVersion = context.versionString()

            platforms += context.platforms().all().values.map { it.id() }
            providers += context.providers().all().values.map { it.id() }

            properties["java.version"] = System.getProperty("java.version", "unknown")
            properties["os.name"] = System.getProperty("os.name", "unknown")
            properties["os.arch"] = System.getProperty("os.arch", "unknown")
        }
    }

    // ── GetBoardInfo ─────────────────────────────────────────────────────────

    override suspend fun getBoardInfo(request: Empty): BoardInfoResponse {
        val info = BoardInfoHelper.current()
        return boardInfoResponse {
            boardModel = info.boardModel?.label ?: "unknown"
            operatingSystem = info.operatingSystem?.name ?: "unknown"
            info.boardModel?.headerVersion?.headerTypes
                ?.flatMap { it.pins }
                ?.forEach { pin ->
                    headerPins += headerPin {
                        name = pin.name.ifEmpty { "GPIO_${pin.bcmNumber ?: 0}" }
                        pinNumber = pin.pinNumber
                        bcmNumber = pin.bcmNumber ?: -1
                        pinType = pin.pinType?.name ?: "GROUND"
                        color = pin.pinType?.color?.toString() ?: ""
                    }
                }
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

    private fun serviceVersion() = Version.SERVICE

    private fun com.pi4j.context.Context.versionString(): String =
        runCatching { this.javaClass.`package`.implementationVersion ?: "4.x" }
            .getOrDefault("unknown")
}
