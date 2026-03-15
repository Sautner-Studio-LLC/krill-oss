package krill.zone.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.*
import krill.zone.shared.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*

@Composable
fun NodeListScreen(type: KrillApp) {
    val nodeManager: ClientNodeManager = koinInject()
    val screenCore: ScreenCore = koinInject()


    nodeManager.selectedNodeId.collectAsState().value?.let { id ->
        val selectedNode = nodeManager.readNodeState(id)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SMALL)
        ) {
            Text(
                text = type.content().shortDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CommonLayout.PADDING_SMALL)
            )
        }


        if (selectedNode.value.type is KrillApp.Client && screenCore.selectedCommand.value is KrillApp.Server) {

            nodeManager.nodes().filter { n -> n.type == KrillApp.Server }.forEach { host ->
                val nodes =
                    nodeManager.nodes().filter { n -> n.type == type && n.type == KrillApp.Server }
                if (nodes.isNotEmpty()) {
                    Row {
                        NodeRow(host) { id ->
                            screenCore.executeCommand(MenuCommand.Update)
                            screenCore.selectNode(id)
                        }
                    }


                }
            }


        } else {

            nodeManager.nodes().filter { n -> n.type == KrillApp.Server }.forEach { host ->
                val nodes =
                    nodeManager.nodes().filter { n -> n.type == type && n.type != KrillApp.Server && n.host == host.id }
                if (nodes.isNotEmpty()) {
                    Row {
                        NodeRow(host) { id ->
                            screenCore.executeCommand(MenuCommand.Update)
                            screenCore.selectNode(id)
                        }
                    }
                    nodes.forEach { r ->
                        Row(modifier = Modifier.fillMaxWidth().padding(start = CommonLayout.PADDING_START_NESTED)) {
                            NodeRow(r) {
                                screenCore.selectNode(r.id)
                                screenCore.executeCommand(MenuCommand.Update)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = CommonLayout.PADDING_SMALL))
                }
            }

        }
    }


}

