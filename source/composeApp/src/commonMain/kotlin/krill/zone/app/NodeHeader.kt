package krill.zone.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.*
import krill.zone.app.IconManager.iconLargeImageModifier
import krill.zone.shared.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*

@Composable
fun NodeHeader() {
    val screenCore: ScreenCore = koinInject()
    val nodeManager: ClientNodeManager = koinInject()
   
    val nodeId =  nodeManager.selectedNodeId.collectAsState()
    nodeId.value?.let { id ->
        if (nodeManager.nodeAvailable(id)) {
            val n = nodeManager.readNodeState(id).collectAsState().value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(CommonLayout.PADDING_LARGE),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(modifier = iconLargeImageModifier, onClick = {
                    screenCore.reset()
                }) {
                    n.icon()
                }
                Text(
                    text = n.type.content().title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = CommonLayout.PADDING_SMALL)
                )
                Spacer(Modifier.weight(1f))

                IconButton(onClick = {
                    screenCore.executeCommand(MenuCommand.Update)
                }) {
                    MenuCommand.Update.icon()
                }

                IconButton(onClick = {
                    screenCore.executeCommand(MenuCommand.Delete)
                    screenCore.reset()
                }) {

                    MenuCommand.Delete.icon()

                }





            }
        }
    }
}