package krill.zone.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import krill.composeapp.generated.resources.*
import krill.zone.shared.node.manager.*
import org.jetbrains.compose.resources.*
import org.koin.compose.*


@Composable
fun SaveNodeButton() {
    val nodeManager: ClientNodeManager = koinInject()
    val screenCore: ScreenCore = koinInject()


    val nodeId =  nodeManager.selectedNodeId.collectAsState()
    Button(
        onClick = {
            try {


                nodeId.value?.let { id ->
                    nodeManager.readNodeStateOrNull(id).value?.let { state ->
                        nodeManager.submit(state)
                    }
                }
                screenCore.reset()
            } catch (_: Exception) {
                // Node not found or error - handle gracefully
            }
        })
    {
        Text(stringResource(Res.string.save))
    }
}