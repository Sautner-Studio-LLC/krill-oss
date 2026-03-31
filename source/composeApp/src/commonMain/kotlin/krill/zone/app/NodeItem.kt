@file:OptIn(ExperimentalUuidApi::class)

package krill.zone.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.*
import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.app.ui.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.llm.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.Node
import krill.zone.shared.node.manager.*
import org.koin.compose.*
import kotlin.time.*
import kotlin.uuid.*

private val logger = Logger.withTag("NodeItem")

/**
 * Renders a single node at its calculated 2D position.
 *
 * REQUIRES BoxScope for absolute 2D positioning:
 * - align(Alignment.Center): Centers the coordinate system
 * - offset(x, y): Positions node at calculated coordinates
 *
 * This pattern cannot be used inside LazyColumn/LazyRow because:
 * - BoxScope.align() is not available in lazy list scopes
 * - LazyColumn ignores offset() modifiers
 * - Lazy lists force linear layouts that break graph visualization
 */
@Composable
fun BoxScope.NodeItem(
    node: Node,
    position: Offset,
    density: Density,
    isNewNode: Boolean,
    isRemoving: Boolean = false
) {
    val nodeManager: ClientNodeManager = koinInject()
    val screenCore: ScreenCore = koinInject()
    val nodeChildren: NodeChildren = koinInject()
    val scope = rememberCoroutineScope()
    val (x, y) = rememberAnimatedPosition(position, density, isNewNode, node)

    fun showMenu() {
        screenCore.selectNode(node.id)
        val children = nodeChildren.load(node)
        logger.i("${node.details()}: clicked menu options: ${children.size} ")
        when {

            node.type is KrillApp.Server.LLM -> {
                // LLM nodes show chat in the avatar speech bubble — no command needed
            }

            node.type is KrillApp.DataPoint.Graph || node.type is KrillApp.Project.Diagram || node.type is KrillApp.Project.Camera -> {
                screenCore.executeCommand(MenuCommand.Expand)
            }

            node.type is KrillApp.Server.Pin -> {
                val meta = node.meta as PinMetaData
                if (meta.pinNumber == 0) {
                    screenCore.executeCommand(MenuCommand.Update)
                }
            }



            children.size <= 1 -> {

                screenCore.executeCommand(MenuCommand.Update)

            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x, y)
            .pointerInput(node.id, isRemoving) {
                // Only handle right-click on platforms that support it (Desktop/WASM)
                if (!isRemoving && supportsRightClick) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Handle secondary (right) click on platforms that support it
                            try {
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    logger.i("Node Right Clicked ${node.details()}")

                                    screenCore.selectNode(node.id, alt = true)
                                    if (node.type is KrillApp.Server.LLM || node.type is KrillApp.Project.Diagram || node.type is KrillApp.Project.Camera) {
                                        screenCore.executeCommand(MenuCommand.Update)
                                    }

                                    event.changes.forEach { it.consume() }
                                }
                            } catch (_: Throwable) {
                                // buttons may not be present on some platforms; ignore
                            }
                        }
                    }
                }
            }
            .pointerInput(node.id, isRemoving) {
                if (!isRemoving) {
                    detectTapGestures(
                        onTap = {
                            scope.launch {
                                // If an LLM chat session is active, intercept taps to build selectedNodes
                                val llmNodeId = nodeManager.selectedNodeId.value
                                val llmNode = nodeManager.readNodeStateOrNull(llmNodeId).value
                                if (llmNode?.type is KrillApp.Server.LLM && node.id != llmNodeId) {
                                    val llmMeta = llmNode.meta as? LLMMetaData ?: return@launch
                                    val identity = NodeIdentity(nodeId = node.id, hostId = node.host)
                                    val updatedNodes = if (llmMeta.selectedNodes.contains(identity)) {
                                        llmMeta.selectedNodes - identity
                                    } else {
                                        llmMeta.selectedNodes + identity
                                    }
                                    nodeManager.submit(llmNode.copy(meta = llmMeta.copy(selectedNodes = updatedNodes)))
                                    return@launch
                                }

                                // Use selectNodeById to always get current state, avoiding stale closure issues
                                when (node.type) {
                                    KrillApp.Trigger.Button -> {
                                        nodeManager.execute(node)
                                    }

                                    KrillApp.DataPoint -> {
                                        val meta = node.meta as DataPointMetaData
                                        if (meta.dataType == DataType.DIGITAL) {
                                            nodeManager.readNodeStateOrNull(node.id).value?.let { n ->
                                                val m = n.meta as DataPointMetaData
                                                val flipped =
                                                    if (m.snapshot.doubleValue() == DigitalState.ON.toDouble()) {
                                                        DigitalState.OFF.toDouble()
                                                    } else {
                                                        DigitalState.ON.toDouble()
                                                    }
                                                logger.i("Flipping Toggle ${m.snapshot.value} -> $flipped")
                                                nodeManager.postSnapshot(
                                                    n,
                                                    Snapshot(
                                                        timestamp = Clock.System.now().toEpochMilliseconds(),
                                                        value = flipped
                                                    )
                                                )
                                            }



                                        }

                                        else {

                                            showMenu()
                                        }

                                    }

                                    else -> {
                                        if (node.meta is TargetingNodeMetaData && (node.meta as TargetingNodeMetaData).executionSource.contains(
                                                ExecutionSource.ON_CLICK)
                                        ) {
                                            nodeManager.execute(node)
                                        } else {
                                            showMenu()
                                        }


                                    }


                                }
                            }
                        },
                        onLongPress = {
                            // Long press always shows menu (all platforms)
                            logger.i("Node Long Pressed ${node.type}")
                            // Use selectNodeById to always get current state
                            screenCore.selectNode(node.id, alt = true)
                            if (node.type is KrillApp.Server.LLM || node.type is KrillApp.Project.Diagram || node.type is KrillApp.Project.Camera) {
                                screenCore.executeCommand(MenuCommand.Update)
                            }
                        }
                    )
                }
            }
    ) {
        AnimatedNodeVisibility(isNewNode = isNewNode, node = node, isRemoving = isRemoving)
    }
}


@Composable
private fun rememberAnimatedPosition(
    position: Offset, density: Density, isNewNode: Boolean, node: Node
): Pair<Dp, Dp> {
    val animatedX by animateDpAsState(
        targetValue = with(density) { position.x.toDp() }, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        ), label = "nodeX_${node.id}"
    )

    val animatedY by animateDpAsState(
        targetValue = with(density) { position.y.toDp() }, animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow
        ), label = "nodeY_${node.id}"
    )

    val x = if (isNewNode) with(density) { position.x.toDp() } else animatedX
    val y = if (isNewNode) with(density) { position.y.toDp() } else animatedY

    return Pair(x, y)
}

@Composable
private fun AnimatedNodeVisibility(isNewNode: Boolean, node: Node, isRemoving: Boolean = false) {


    val enterAnimation = if (isNewNode) {
        fadeIn(animationSpec = tween(durationMillis = 600, delayMillis = 100)) + scaleIn(
            animationSpec = tween(
                durationMillis = 600,
                delayMillis = 100
            ), initialScale = 0.3f
        )
    } else {
        fadeIn(animationSpec = tween(0))
    }

    val exitAnimation = fadeOut(animationSpec = tween(durationMillis = 300)) + scaleOut(
        animationSpec = tween(durationMillis = 300),
        targetScale = 0.3f
    )


    // Node is visible unless it's being removed
    AnimatedVisibility(
        visible = !isRemoving, enter = enterAnimation, exit = exitAnimation
    ) {


        if (node.type == KrillApp.Server ||
            node.type == KrillApp.Server.Peer ||
            node.type == KrillApp.DataPoint ||
            node.type == KrillApp.Project ||
            node.type == KrillApp.Project.TaskList ||
            node.type == KrillApp.Project.Journal ||
            node.type == KrillApp.Project.Diagram ||
            node.type == KrillApp.Server.Pin

        ) {

            val text = node.name()


            // Use Box with wrapContentSize(unbounded=true) to allow NamePill to overflow
            // without affecting the icon's centered position
            Box(
                contentAlignment = Alignment.Center
            ) {
                // Icon stays at the center (the anchor point for node positioning)
                node.icon()
                // NamePill positioned above the icon using offset, doesn't affect layout
                if (text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = -(CommonLayout.ICON_SIZE_LARGE / 2))
                    ) {
                        NamePill(name = text)
                    }
                }

            }
        } else {
            node.icon()
        }
    }
}
