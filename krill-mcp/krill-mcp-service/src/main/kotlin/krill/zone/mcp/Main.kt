package krill.zone.mcp

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfigLoader
import krill.zone.mcp.http.startMcpServer
import krill.zone.mcp.krill.KrillRegistry
import krill.zone.mcp.mcp.McpServer
import krill.zone.mcp.mcp.tools.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("krill-mcp")

private const val SERVER_VERSION = "0.0.4"

fun main() {
    log.info("Starting krill-mcp version={}", SERVER_VERSION)

    val config = KrillMcpConfigLoader.load()
    val pin = PinProvider(path = config.pinDerivedKeyPath)
    if (!pin.isConfigured()) {
        log.warn(
            "No PIN-derived key at {}. MCP will start but all requests will be rejected as unauthorized. " +
                "Run `sudo dpkg-reconfigure krill-mcp` or write the derived key manually.",
            config.pinDerivedKeyPath,
        )
    }

    val registry = KrillRegistry(config, pin)
    runBlocking { registry.bootstrap() }

    val tools = listOf(
        ListServersTool(registry),
        ListNodesTool(registry),
        GetNodeTool(registry),
        ReadSeriesTool(registry),
        ServerHealthTool(registry),
        ListProjectsTool(registry),
        CreateProjectTool(registry),
        CreateDiagramTool(registry),
        UpdateDiagramTool(registry),
        GetDiagramTool(registry),
        UploadDiagramFileTool(registry),
        DownloadDiagramFileTool(registry),
    )

    val mcp = McpServer(
        serverName = "krill-mcp",
        serverVersion = SERVER_VERSION,
        tools = tools,
    )

    val httpServer = startMcpServer(config.listenPort, mcp, pin)
    log.info("krill-mcp listening on :{} (tools={})", config.listenPort, tools.size)

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down krill-mcp")
        httpServer.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
    })

    // Block main thread — Ktor runs on its own dispatcher.
    Thread.currentThread().join()
}
