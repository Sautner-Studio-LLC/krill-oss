package krill.zone.app.startup.wasm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import co.touchlab.kermit.*
import krill.zone.app.*
import krill.zone.app.krillapp.server.*
import krill.zone.app.startup.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.krillapp.client.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*
import kotlin.uuid.*


private val logger = Logger.withTag("WASM")

/**
 * WASM-specific app content that handles browser first-time user experience.
 *
 * Lifecycle:
 * 1. Client node exists (created by ClientNodeManager.init() in App.kt) with ftue=true on first visit
 * 2. FTUE: Show ConnectBrowser where user accepts TOS, enters API key, tests connection
 * 3. On successful test + Continue: save server node with API key, set client ftue=false,
 *    then execute client node to trigger ClientClientProcessor startup flow
 * 4. Subsequent visits: client ftue=false, App.kt's onReady executes client node,
 *    ClientClientProcessor loads stored server and connects via serverConnector —
 *    we just wait for a server to appear in the swarm, then show KrillScreen
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun WasmAppContent() {
    val nodeManager: ClientNodeManager = koinInject()
    val fileOperations: FileOperations = koinInject()

    // Read FTUE state reactively from the client node that was created by nodeManager.init()
    val clientNode = nodeManager.readNodeState(installId()).collectAsState()
    val clientMeta = clientNode.value.meta as ClientMetaData

    // Kiosk mode: api_key in URL — skip FTUE and auto-connect
    val kioskApiKey = SystemInfo.wasmApiKey
    logger.i { "WASM Api key $kioskApiKey" }
    if (clientMeta.ftue && kioskApiKey != null) {
        logger.i { "Kiosk mode — auto-connecting with API key from URL" }
        KioskAutoConnect(
            apiKey = kioskApiKey,
            onConnected = { serverNode ->
                fileOperations.update(serverNode)
                nodeManager.update(serverNode)
                nodeManager.updateMetaData(
                    clientNode.value,
                    clientMeta.copy(ftue = false)
                )
                logger.i { "Kiosk auto-connect complete — saved server ${serverNode.details()}, ftue=false" }
                nodeManager.execute(clientNode.value)
            }
        )
    } else if (clientMeta.ftue) {
        // FTUE: Show the welcome/setup screen
        logger.i { "FTUE active — showing ConnectBrowser" }
        ConnectBrowser(
            onConnected = { serverNode ->
                // Save the server node returned from health check (has real server ID, metadata)
                fileOperations.update(serverNode)
                nodeManager.update(serverNode)

                // Mark FTUE complete on the client node — this triggers recomposition
                // into the else branch
                nodeManager.updateMetaData(
                    clientNode.value,
                    clientMeta.copy(ftue = false)
                )
                logger.i { "FTUE complete — saved server ${serverNode.details()}, ftue=false" }

                // Execute the client node to trigger ClientClientProcessor startup,
                // which loads stored servers and connects via serverConnector
                nodeManager.execute(clientNode.value)
            }
        )
    } else {
        // Post-FTUE: ClientClientProcessor startup flow (triggered by App.kt onReady)
        // is already connecting to stored servers. Just wait for a server to appear.
        WasmAwaitServer(nodeManager, fileOperations, clientNode.value, clientMeta)
    }
}

/**
 * Kiosk auto-connect: uses the API key from the URL query string to call /health,
 * then invokes [onConnected] with the verified server Node. Shows a spinner while
 * connecting and an error message with retry on failure.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun KioskAutoConnect(
    apiKey: String,
    onConnected: (serverNode: Node) -> Unit
) {
    val nodeHttp: NodeHttp = koinInject()
    var error by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(retryTrigger) {
        error = null
        try {
            val serverMeta = ServerMetaData(
                name = hostName,
                port = SystemInfo.wasmPort,
                apiKey = apiKey
            )
            val tempServer = NodeBuilder()
                .type(KrillApp.Server)
                .meta(serverMeta)
                .id("_")
                .parent("_")
                .host("_")
                .create()

            nodeHttp.readHealth(tempServer)?.let { healthNode ->
                val healthMeta = healthNode.meta as ServerMetaData
                val completeServer = healthNode.copy(
                    meta = healthMeta.copy(apiKey = apiKey)
                )

                logger.i { "Kiosk connected to ${healthMeta.name} (${healthNode})" }
                onConnected(completeServer)
            }

        } catch (e: Exception) {
            logger.e(e) { "Kiosk auto-connect failed" }
            error = e.message ?: "Connection failed"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_LARGE)
        ) {
            if (error != null) {
                Text(
                    text = "Kiosk connection failed: $error",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { retryTrigger++ }) {
                    Text("Retry")
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.width(CommonLayout.PROGRESS_INDICATOR_SIZE),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "Connecting to server...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Waits for ClientClientProcessor to connect a server (via the normal startup flow),
 * then shows KrillScreen. Shows a spinner while waiting. Falls back to ConnectBrowser
 * if no stored server exists.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun WasmAwaitServer(
    nodeManager: ClientNodeManager,
    fileOperations: FileOperations,
    clientNode: Node,
    clientMeta: ClientMetaData
) {
    // Check if we have a stored server at all
    val hasStoredServer = remember {
        fileOperations.load().any { it.type is KrillApp.Server }
    }

    if (!hasStoredServer) {
        // Edge case: ftue=false but no server stored — recover by resetting FTUE
        logger.e { "No stored server found but ftue=false — resetting FTUE" }
        LaunchedEffect(Unit) {
            nodeManager.updateMetaData(clientNode, clientMeta.copy(ftue = true))
        }
        return
    }

    // Observe the swarm for any server node to appear
    val swarm by nodeManager.swarm.collectAsState()
    val hasServer = swarm.any { id ->
        nodeManager.nodeAvailable(id) && nodeManager.readNodeState(id).value.type is KrillApp.Server
    }

    if (hasServer) {
        KrillScreen()
    } else {
        // Show spinner while ClientClientProcessor connects
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_LARGE)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(CommonLayout.PROGRESS_INDICATOR_SIZE),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "Connecting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
