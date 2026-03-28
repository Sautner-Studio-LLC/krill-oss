package krill.zone.app.startup.wasm

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import co.touchlab.kermit.*
import krill.zone.app.*
import krill.zone.app.krillapp.client.ftue.*
import krill.zone.app.startup.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.krillapp.client.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import krill.zone.shared.security.*
import org.koin.compose.*
import org.koin.core.context.*
import kotlin.uuid.*


private val logger = Logger.withTag("WASM")

/**
 * WASM-specific app content — follows the same flow as native platforms:
 *
 * 1. FTUE: Accept TOS (WelcomeDialog)
 * 2. PIN entry (PinEntryScreen) — stored in browser localStorage
 * 3. Auto-connect to host server using browser's hostname
 * 4. Show KrillScreen
 *
 * On subsequent visits: PIN is loaded from localStorage, auto-connect proceeds immediately.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
fun WasmAppContent() {
    val nodeManager: ClientNodeManager = koinInject()
    val fileOperations: FileOperations = koinInject()
    val pinStore: ClientPinStore? = GlobalContext.get().getOrNull()

    val clientNode = nodeManager.readNodeState(installId()).collectAsState()
    val clientMeta = clientNode.value.meta as ClientMetaData

    val showFtue = remember { mutableStateOf(clientMeta.ftue) }
    val needsPin = remember { mutableStateOf(pinStore?.bearerToken() == null) }

    when {
        // Step 1: TOS acceptance
        showFtue.value -> {
            logger.i { "WASM FTUE — showing TOS" }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = CommonLayout.SERVER_FORM_MAX_WIDTH)
                        .padding(CommonLayout.PADDING_LARGE)
                ) {
                    Column(modifier = Modifier.padding(CommonLayout.PADDING_LARGE)) {
                        WelcomeDialog {
                            nodeManager.updateMetaData(
                                nodeManager.readNodeState(clientNode.value.id).value,
                                clientMeta.copy(ftue = false)
                            )
                            showFtue.value = false
                        }
                    }
                }
            }
        }

        // Step 2: PIN entry
        needsPin.value -> {
            logger.i { "WASM — showing PIN entry" }
            PinEntryScreen(
                onPinEntered = { pin ->
                    pinStore?.storePin(pin)
                    needsPin.value = false
                }
            )
        }

        // Step 3: Connect and show app
        else -> {
            WasmConnectAndShow(nodeManager, fileOperations, clientNode.value, clientMeta)
        }
    }
}

/**
 * Handles the connection flow after TOS + PIN are complete.
 * If no server is stored yet, auto-connects to the host server.
 * Then waits for the server to appear in the swarm and shows KrillScreen.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun WasmConnectAndShow(
    nodeManager: ClientNodeManager,
    fileOperations: FileOperations,
    clientNode: Node,
    clientMeta: ClientMetaData
) {
    val hasStoredServer = remember {
        fileOperations.load().any { it.type is KrillApp.Server }
    }

    // If no stored server, auto-connect to the host (the Ktor server serving this WASM app)
    if (!hasStoredServer) {
        val nodeHttp: NodeHttp = koinInject()
        var connecting by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            try {
                val serverMeta = ServerMetaData(
                    name = SystemInfo.wasmHost,
                    port = SystemInfo.wasmPort,
                )
                val tempServer = NodeBuilder()
                    .type(KrillApp.Server)
                    .meta(serverMeta)
                    .id("_").parent("_").host("_")
                    .create()

                nodeHttp.readHealth(tempServer)?.let { healthNode ->
                    fileOperations.update(healthNode)
                    nodeManager.update(healthNode)
                    logger.i { "WASM auto-connect complete — ${healthNode.details()}" }
                    nodeManager.execute(clientNode)
                    connecting = false
                } ?: run {
                    error = "Server not reachable"
                    connecting = false
                }
            } catch (e: Exception) {
                logger.e(e) { "WASM auto-connect failed" }
                error = e.message ?: "Connection failed"
                connecting = false
            }
        }

        if (connecting || error != null) {
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
                            text = "Connection failed: $error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.width(CommonLayout.PROGRESS_INDICATOR_SIZE),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text("Connecting to server...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            return
        }
    }

    // Wait for server to appear in swarm, then show KrillScreen
    val swarm by nodeManager.swarm.collectAsState()
    val hasServer = swarm.any { id ->
        nodeManager.nodeAvailable(id) && nodeManager.readNodeState(id).value.type is KrillApp.Server
    }

    if (hasServer) {
        KrillScreen()
    } else {
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
                Text("Connecting...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
