package krill.zone.app

import co.touchlab.kermit.*
import kotlinx.coroutines.flow.*
import krill.zone.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*
import kotlin.uuid.*


interface ScreenCore {


    val authDialog: StateFlow<Boolean>

    val avatareSpeechBubbleContent: StateFlow<String>

    fun announceDialog(text: String)

    val selectedCommand: StateFlow<KrillApp?>


    fun selectNode(nodeId: String?, alt: Boolean = false)

    fun executeCommand(type: KrillApp)
    fun reset()


}

class DefaultScreenCore(private val nodeManager: ClientNodeManager) : ScreenCore {
    private val logger = Logger.withTag(this::class.getFullName())

    private val _authDialog: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val authDialog: StateFlow<Boolean> = _authDialog

    private val _demoDialog: MutableStateFlow<String> = MutableStateFlow("")
    override val avatareSpeechBubbleContent: StateFlow<String> = _demoDialog

    private val _selectedCommand: MutableStateFlow<KrillApp?> = MutableStateFlow(null)
    override val selectedCommand: StateFlow<KrillApp?> = _selectedCommand

    @OptIn(ExperimentalUuidApi::class)
    override fun executeCommand(type: KrillApp) {
        val nodeId = nodeManager.selectedNodeId.value
        val selectedNodeType = nodeManager.readNodeStateOrNull(nodeId).value?.type
        logger.i("v2 menu selected: $selectedNodeType $type ")
        when {
            type.isMenuOption() -> {
                when (type) {
                    is MenuCommand.Delete -> {
                        nodeId?.let { id ->
                            if (nodeManager.nodeAvailable(id)) {
                                nodeManager.delete(nodeManager.readNodeState(id).value)
                            }
                        }
                        selectNode(null)
                    }

                    else -> {
                        nodeId?.let { id ->
                            if (nodeManager.nodeAvailable(id)) {
                                nodeManager.readNodeStateOrNull(id).value?.let { node ->
                                    nodeManager.editing(node)
                                }

                            }
                        }

                        _selectedCommand.update { type }
                    }
                }

            }

            selectedNodeType == KrillApp.Client -> {

                _selectedCommand.update { type }
            }

            type is KrillApp.Server.Peer -> {
                //user selected to add a peer to a server
                _selectedCommand.update { type }

            }

            else -> {
                nodeId?.let { id ->
                    if (nodeManager.nodeAvailable(id)) {
                        val n = nodeManager.readNodeState(id).value
                        nodeManager.create(
                            NodeBuilder()
                                .node(type.node())
                                .parent(n.id)
                                .host(n.host)
                                .create()
                        )
                        // If the parent is a full-screen node (Project, Diagram, Camera),
                        // stay on that screen so the user sees the new child appear.
                        // Otherwise reset to swarm view (original behavior for ClientScreen menus).
                        if (n.type is KrillApp.Project || n.type is KrillApp.Project.Diagram || n.type is KrillApp.Project.Camera) {
                            // Keep selection on the parent — ProjectScreen will recompose with the new child
                        } else {
                            reset()
                        }
                    }
                }
            }
        }
    }


    override fun announceDialog(text: String) {
        _demoDialog.value = text
    }

    override fun selectNode(nodeId: String?, alt: Boolean) {

        nodeManager.selectNode(nodeId)


    }


    override fun reset() {
        nodeManager.selectedNodeId.value?.let { nodeId ->
            if (nodeManager.nodeAvailable(nodeId)) {
                nodeManager.readNodeStateOrNull(nodeId).value?.let { node ->
                    if (node.state == NodeState.EDITING) {
                        nodeManager.reset(node)
                    }
                }
            }
        }
        nodeManager.selectNode(null)
        _selectedCommand.update { null }
    }


}
