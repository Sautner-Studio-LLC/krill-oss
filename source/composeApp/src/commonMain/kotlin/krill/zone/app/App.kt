package krill.zone.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import co.touchlab.kermit.*
import krill.zone.app.startup.*
import krill.zone.app.startup.wasm.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*
import kotlin.uuid.*

private val logger = Logger.withTag("App")

@OptIn(ExperimentalUuidApi::class)
@Composable
fun App() {
    DarkBlueGrayTheme {
        WithEmojiSupport {
            AppScaffold()
        }
    }
}

@Composable
fun AppScaffold() {
    val nodeManager: ClientNodeManager = koinInject()
    val ready = remember { mutableStateOf(false) }
    val padding = if (isMobile) CommonLayout.PADDING_MOBILE_TOP else 0.dp

    LaunchedEffect(Unit) {
        logger.i("launching background process $platform")
        nodeManager.init {
            val client = nodeManager.readNodeState(installId()).value
            logger.i("${client.details()}: app ready. $client")
            nodeManager.execute(client)
            ready.value = true

        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(top = padding)
        ) {

            if (!ready.value) {
                CircularProgressIndicator(
                    modifier = Modifier.width(CommonLayout.PROGRESS_INDICATOR_SIZE).align(Alignment.Center),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            } else {
                if (platform == Platform.WASM) {
                    WasmAppContent()
                } else {
                    KrillScreen()
                }

            }
        }
    }
}



