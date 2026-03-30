package krill.zone.app.startup

import androidx.compose.runtime.*
import krill.zone.app.*
import krill.zone.app.krillapp.client.*
import krill.zone.app.krillapp.client.about.*
import krill.zone.app.krillapp.project.diagram.*
import krill.zone.app.krillapp.server.*
import krill.zone.app.krillapp.server.peer.*
import krill.zone.app.ui.*
import krill.zone.shared.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*
import kotlin.uuid.*

@OptIn(ExperimentalUuidApi::class)
@Composable
fun KrillScreen() {
    val screenCore: ScreenCore = koinInject()
    val nodeManager: ClientNodeManager = koinInject()
    val selectedNodeId =  nodeManager.selectedNodeId.collectAsState()
    val command = screenCore.selectedCommand.collectAsState()

    // Get node type from nodeManager if we have a selected node
    val nodeType = selectedNodeId.value?.let { id ->
        if (nodeManager.nodeAvailable(id)) {
            nodeManager.readNodeState(id).collectAsState().value.type
        } else null
    }

    // Determine if we should show ClientScreen - consolidate all paths that lead to ClientScreen
    // to ensure it's always called from the same composition location
    val showClientScreen = when {
        nodeType == KrillApp.Server && command.value == KrillApp.Server.Peer -> false
        nodeType == KrillApp.Client && command.value == MenuCommand.Focus -> true
        nodeType == KrillApp.Project.Diagram && command.value !in listOf(MenuCommand.Expand, MenuCommand.Update) -> true
        nodeType == null && command.value !in listOf(MenuCommand.Update, MenuCommand.Expand) -> true
        nodeType != null && nodeType != KrillApp.Client && nodeType != KrillApp.Project.Diagram &&
                command.value == MenuCommand.Focus && selectedNodeId.value == null -> true

        nodeType != null && nodeType != KrillApp.Client && nodeType != KrillApp.Project.Diagram &&
                command.value !in listOf(MenuCommand.Update, MenuCommand.Expand, MenuCommand.Focus) -> true

        else -> false
    }

    if (showClientScreen) {
        // Always render ClientScreen from this single location to preserve state
        ClientScreen()
    } else {
        // Handle all other screen types
        when (nodeType) {
            KrillApp.Client -> {
                when (command.value) {
                    KrillApp.Client.About -> {
                        command.value?.let { c ->
                            ScreenContainer(c) {
                                AboutScreen()
                            }
                        }
                    }

                    KrillApp.Server.Peer -> {
                        command.value?.let { c ->
                            ScreenContainer(c) {
                                PeerScreen()
                            }
                        }
                    }

                    KrillApp.Server -> {

                        NodeEditorContainer(ViewMode.EDIT) {
                            EditServer(null)
                        }

                    }


                    else -> {
                        command.value?.let { c ->
                            ScreenContainer(c) {
                                NodeListScreen(c)
                            }
                        }
                    }
                }
            }

            KrillApp.Server -> {
                when (command.value) {
                    KrillApp.Server.Peer -> {
                        NodeEditorContainer(ViewMode.EDIT) {
                            ConnectPeer()
                        }

                    }

                    else -> {
                        selectedNodeId.value?.let { id ->
                            if (nodeManager.nodeAvailable(id)) {
                                NodeSummaryAndEditor(nodeManager.readNodeState(id).value, ViewMode.EDIT)
                            }
                        }
                    }
                }

            }


            KrillApp.Project.Diagram -> {
                selectedNodeId.value?.let { id ->
                    nodeManager.readNodeStateOrNull(id).value?.let { n ->
                        DiagramScreen(n)
                    }
                }


            }


            else -> {
                when (command.value) {
                    MenuCommand.Update -> {
                        selectedNodeId.value?.let { id ->
                            if (nodeManager.nodeAvailable(id)) {
                                NodeSummaryAndEditor(nodeManager.readNodeState(id).value, ViewMode.EDIT)
                            }
                        }
                    }

                    MenuCommand.Expand -> {
                        selectedNodeId.value?.let { id ->
                            if (nodeManager.nodeAvailable(id)) {
                                NodeSummaryAndEditor(nodeManager.readNodeState(id).value, ViewMode.EDIT)
                            }
                        }
                    }

                    MenuCommand.Focus -> {
                        selectedNodeId.value?.let { id ->
                            if (nodeManager.nodeAvailable(id)) {
                                NodeSummaryAndEditor(nodeManager.readNodeState(id).value, ViewMode.EDIT)
                            }
                        }
                    }

                    else -> { /* Handled by showClientScreen */
                    }
                }
            }
        }
    }
}
