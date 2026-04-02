package krill.zone.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import co.touchlab.kermit.*
import krill.zone.*
import krill.zone.app.NodeViewConstants.NODE_MENU_ANIMATION_DURATION_MS
import krill.zone.shared.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*

private val logger = Logger.withTag("NodeMenu")

@Composable
fun NodeMenu(menuItems: List<KrillApp>) {

    val screenCore: ScreenCore = koinInject()
    val nodeManager: ClientNodeManager = koinInject()
    val selectedNodeId = nodeManager.selectedNodeId.collectAsState()
    val selectedNode = nodeManager.readNodeStateOrNull(selectedNodeId.value).collectAsState()
    logger.i { "showing node menu ${selectedNode.value}" }
    selectedNode.value?.let { node ->

        if (menuItems.isNotEmpty()) {

                AnimatedVisibility(
                    visible = true,
                    enter = expandIn(animationSpec = tween(NODE_MENU_ANIMATION_DURATION_MS)) +
                            fadeIn(animationSpec = tween(NODE_MENU_ANIMATION_DURATION_MS)),
                    exit = shrinkOut(animationSpec = tween(NODE_MENU_ANIMATION_DURATION_MS)) +
                            fadeOut(animationSpec = tween(NODE_MENU_ANIMATION_DURATION_MS))
                ) {
                    NodeMenuContent(node, menuItems, screenCore)
                }

        }
    }
}

@Composable
private fun NodeMenuContent(node: Node, menuItems: List<KrillApp>, screenCore: ScreenCore) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SERVER_LIST)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CommonLayout.PADDING_EXTRA_SMALL),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    NodeSummaryAndEditor(node, ViewMode.ROW)
                }
            }
            logger.i { "${node.details()}: showing avatar menu with ${menuItems.size} items" }
            HorizontalDivider()
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                menuItems.forEach { command ->
                    Column(
                        modifier = Modifier
                            .clickable(
                                enabled = true,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    screenCore.executeCommand(command)
                                }
                            ),
                    ) {
                        command.icon()
                    }
                }
            }
        }
    }
}


