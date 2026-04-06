package krill.zone.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.painter.*
import androidx.compose.ui.text.style.*
import co.touchlab.kermit.*
import krill.composeapp.generated.resources.*
import krill.zone.app.NodeViewConstants.NODE_LABEL_BACKGROUND_ALPHA
import krill.zone.app.NodeViewConstants.NODE_LABEL_CORNER_SHAPE
import krill.zone.app.NodeViewConstants.NODE_LABEL_FONT_SIZE
import krill.zone.app.NodeViewConstants.NODE_LABEL_HORIZONTAL_PADDING
import krill.zone.app.NodeViewConstants.NODE_LABEL_MAX_LENGTH
import krill.zone.app.NodeViewConstants.NODE_LABEL_TEXT_ALPHA
import krill.zone.app.NodeViewConstants.NODE_LABEL_VERTICAL_PADDING
import krill.zone.app.NodeViewConstants.NODE_LABEL_Y_OFFSET
import krill.zone.app.krillapp.server.pin.*
import krill.zone.shared.*
import krill.zone.shared.feature.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.trigger.color.*
import krill.zone.shared.krillapp.executor.logicgate.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.jetbrains.compose.resources.*
import org.koin.compose.*

private val logger = Logger.withTag("IconManager")

fun KrillApp.title() : String {
    return this.resourceName().substringAfterLast(".")
}


val Node.icon: @Composable (() -> Unit)
    get() = @Composable {
        IconManager.NodeCompoundImage(Modifier, this)
    }

val KrillApp.icon: @Composable (() -> Unit)
    get() = @Composable {
        // Menu items don't get highlight effects (rings, etc.)
        IconManager.NodeCompoundImage(Modifier, this.node(), highlight = false)
    }

val Node.label: @Composable (() -> Unit)

    get() = @Composable {

        NodeLabel(this.type.title())
    }


object IconManager {

    private val iconSize = CommonLayout.ICON_SIZE_STANDARD

    private val iconLargeSize = CommonLayout.ICON_SIZE_LARGE
    private val iconEnclosureSize = CommonLayout.ICON_SIZE_LARGE
    val iconImageModifier = Modifier.size(iconSize)

    val iconLargeImageModifier = Modifier.size(iconLargeSize)
    val iconEnclosureModifier = Modifier.size(iconEnclosureSize)

    val iconColorFilter @Composable get() = ColorFilter.tint(colorScheme.onSecondaryContainer)

    @Composable
    private fun nodeIcon(node: Node): Painter {

        return when (node.type) {
            KrillApp.Client -> painterResource(Res.drawable.shrimp)
            KrillApp.Client.About -> painterResource(Res.drawable.question_duotone_regular_full)
            KrillApp.Server -> painterResource(Res.drawable.server_duotone_regular_full)
            KrillApp.DataPoint -> {
                val meta = node.meta as DataPointMetaData
                when (meta.dataType) {
                    DataType.TEXT -> painterResource(Res.drawable.file_brackets_curly_duotone_regular)
                    DataType.JSON -> painterResource(Res.drawable.file_brackets_curly_duotone_regular)
                    DataType.DIGITAL -> {

                        if (meta.snapshot.doubleValue() == DigitalState.ON.toDouble()) {
                            painterResource(Res.drawable.toggle_on_duotone_regular)
                        } else {
                            painterResource(Res.drawable.toggle_off_duotone_regular)
                        }

                    }

                    DataType.DOUBLE -> painterResource(Res.drawable.empty_duotone_regular_full)
                    DataType.COLOR -> painterResource(Res.drawable.circles_overlap_3_duotone_regular)
                }

            }

            KrillApp.Executor.Calculation -> painterResource(Res.drawable.calculator_simple_duotone_regular_full)
            KrillApp.Executor.Compute -> painterResource(Res.drawable.microchip_sharp_regular_full)

            KrillApp.Project -> painterResource(Res.drawable.diagram_project_duotone_solid_full)
            KrillApp.Project.Diagram -> painterResource(Res.drawable.file_vector_duotone_regular)
            KrillApp.Project.TaskList -> painterResource(Res.drawable.check_double_sharp_duotone_solid_full)
            KrillApp.Project.Journal -> painterResource(Res.drawable.notebook_duotone_solid_full)


            KrillApp.Server.SerialDevice -> painterResource(Res.drawable.usb_brands_solid_full)



            KrillApp.Server.Pin -> {
                val meta = node.meta as PinMetaData

                if (meta.hardwareId.isEmpty() || meta.pinNumber == 0) {
                    painterResource(Res.drawable.raspberry_pi_brands)
                } else {
                    painterResource(Res.drawable.empty_duotone_regular_full)
                }

            }


            KrillApp.DataPoint.Filter -> painterResource(Res.drawable.filters_duotone_regular)
            KrillApp.DataPoint.Filter.Deadband -> painterResource(Res.drawable.skull_cow_duotone_regular_full)
            KrillApp.DataPoint.Filter.Debounce -> painterResource(Res.drawable.reply_clock_duotone_regular_full)
            KrillApp.DataPoint.Filter.DiscardAbove -> painterResource(Res.drawable.gauge_max_duotone_regular_full)
            KrillApp.DataPoint.Filter.DiscardBelow -> painterResource(Res.drawable.gauge_min_duotone_regular_full)
            KrillApp.DataPoint.Graph -> painterResource(Res.drawable.chart_line_duotone_solid_full)
            KrillApp.Trigger.SilentAlarmMs -> painterResource(Res.drawable.alarm_snooze_duotone_regular)
            KrillApp.Trigger.LowThreshold -> painterResource(Res.drawable.gauge_low_duotone_regular_full)
            KrillApp.Trigger.HighThreshold -> painterResource(Res.drawable.gauge_high_duotone_regular_full)
            KrillApp.Trigger.Color -> painterResource(Res.drawable.circles_overlap_3_duotone_regular)


            KrillApp.Executor -> painterResource(Res.drawable.bolt_duotone_regular_full)

            KrillApp.Trigger -> painterResource(Res.drawable.bolt_duotone_regular_full)

            KrillApp.Executor.Lambda -> painterResource(Res.drawable.python_brands_solid_full)
            KrillApp.Trigger.IncomingWebHook -> painterResource(Res.drawable.triple_chevrons_down_sharp_duotone_solid_full)
            KrillApp.Executor.OutgoingWebHook -> painterResource(Res.drawable.triple_chevrons_up_duotone_solid_full)
            KrillApp.Executor.SMTP -> painterResource(Res.drawable.arrow_up_right_from_square_duotone_regular)
            KrillApp.Trigger.CronTimer -> painterResource(Res.drawable.clock_duotone_regular_full)

            MenuCommand.Focus -> painterResource(Res.drawable.maximize_duotone_regular)
            MenuCommand.Update -> painterResource(Res.drawable.pencil_duotone_solid_full)
            MenuCommand.Delete -> painterResource(Res.drawable.trash_duotone_solid_full)
            MenuCommand.Expand -> painterResource(Res.drawable.maximize_duotone_regular)

            KrillApp.Trigger.Button -> painterResource(Res.drawable.play_duotone_regular_full)

            KrillApp.Executor.LogicGate -> {
                val meta = node.meta as LogicGateMetaData
                when (meta.gateType) {
                    LogicGate.AND -> painterResource(Res.drawable.gate_and_duotone_regular_full)
                    LogicGate.OR -> painterResource(Res.drawable.gate_or_duotone_regular_full)
                    LogicGate.BUFFER -> painterResource(Res.drawable.gate_buffer_duotone_regular_full)
                    LogicGate.NOT -> painterResource(Res.drawable.gate_not_duotone_regular_full)
                    LogicGate.NAND -> painterResource(Res.drawable.gate_nand_duotone_regular_full)
                    LogicGate.NOR -> painterResource(Res.drawable.gate_nor_duotone_regular_full)
                    LogicGate.XOR -> painterResource(Res.drawable.gate_xor_duotone_regular_full)
                    LogicGate.XNOR -> painterResource(Res.drawable.gate_xnor_duotone_regular_full)
                    LogicGate.IMPLY -> painterResource(Res.drawable.gate_imply_duotone_regular_full)
                    LogicGate.NIMPLY -> painterResource(Res.drawable.gate_nimply_duotone_regular_full)

                }
            }

            KrillApp.MQTT -> painterResource(Res.drawable.chart_network_duotone_regular)
            KrillApp.Server.Peer -> painterResource(Res.drawable.network_wired_duotone_solid)
            KrillApp.Server.LLM -> painterResource(Res.drawable.brain_circuit_duotone_regular)
            KrillApp.Project.Camera -> painterResource(Res.drawable.camera_security_duotone_regular)
            KrillApp.Server.Backup -> painterResource(Res.drawable.clone_duotone_regular)
        }
    }



    /**
     * Renders the node's inner icon. On WASM, painterResource() loads SVGs asynchronously
     * and triggers recomposition of its own scope when data arrives. By calling nodeIcon()
     * and Image() in the same composable scope, the Image recomposes automatically when
     * the resource finishes downloading — avoiding the race condition where the painter
     * loads after initial composition but nothing triggers a repaint.
     */
    @Composable
    fun NodeIconImage(node: Node) {
        val painter = nodeIcon(node)
        key(painter.intrinsicSize) {
            Image(
                modifier = iconImageModifier,
                painter = painter,
                contentDescription = node.type.title(),
                colorFilter = iconColorFilter,
            )
        }
    }


    @Composable
    fun NodeCompoundImage(modifier: Modifier = Modifier, node: Node, highlight: Boolean = true) {


        val screenCore: ScreenCore = koinInject()
        val nodeManager: ClientNodeManager = koinInject()
        val selectedNodeId = nodeManager.selectedNodeId.collectAsState()


        val isSelected = node.id == selectedNodeId.value

        val primaryColor = colorScheme.primary

        // Animate the glow effect
        val transition = rememberInfiniteTransition()
        val glowAlpha by transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )

        val glowModifier = if (isSelected) {
            Modifier.drawBehind {
                val glowRadius = size.minDimension * 0.6f
                val center = Offset(size.width / 2f, size.height / 2f)
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = glowAlpha),
                        primaryColor.copy(alpha = glowAlpha * 0.5f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = glowRadius
                )
                drawCircle(brush = glowBrush, radius = glowRadius, center = center)
            }
        } else {
            Modifier
        }

        Box(
            modifier = glowModifier
                .then(iconEnclosureModifier)
                .then(modifier),
            contentAlignment = Alignment.Center
        ) {
            val stateColor = getNodeStateColor(node)
            val circlePainter = painterResource(Res.drawable.circle_duotone_regular_full)
            key(circlePainter.intrinsicSize) {
                Image(
                    modifier = Modifier.size(iconEnclosureSize),
                    painter = circlePainter,
                    contentDescription = node.type.title(),
                    colorFilter = ColorFilter.tint(stateColor)
                )
            }

            // Project nodes get a bright accent border ring in the swarm view (not menus)
            if (node.type is KrillApp.Project && highlight) {
                val ringColor = Color(0xFF3DD9A0) // Bright mint — visible on dark backgrounds
                Canvas(modifier = Modifier.size(iconEnclosureSize)) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.6f),
                        radius = size.minDimension / 2f - 1f,
                        style = Stroke(width = 2f)
                    )
                }
            }

            NodeIconImage(node)

            when (node.type) {
                is KrillApp.DataPoint -> {
                    val meta = (node.meta as DataPointMetaData)
                    if (meta.dataType == DataType.DOUBLE) {
                        Text(meta.snapshot.value.take(4), style = MaterialTheme.typography.labelSmall)
                    }
                    if (meta.dataType == DataType.COLOR) {
                        val argb = node.snapshotColorArgb()
                        Canvas(modifier = Modifier.size(iconEnclosureSize * 0.6f)) {
                            drawCircle(color = Color(argb.toInt()), radius = size.minDimension / 2f)
                        }
                    }
                }

                is KrillApp.Trigger.Color -> {
                    val meta = (node.meta as ColorTriggerMetaData)
                    val argb = meta.midpointArgb()
                    Canvas(modifier = Modifier.size(iconEnclosureSize * 0.6f)) {
                        drawCircle(color = Color(argb.toInt()), radius = size.minDimension / 2f)
                    }
                }

                is KrillApp.Server.Pin -> {
                    val meta = (node.meta as PinMetaData)
                    if (meta.hardwareId.isNotEmpty() && meta.pinNumber > 0) {

                        HardwareStatusDot(modifier = modifier, meta, iconSize) {
                            logger.i("hardware dot selected ${node.state}")
                            // Use selectNode with node.id to always get current state
                            screenCore.selectNode(node.id)
                        }
                    }
                }


                else -> {}
            }


        }
    }


}


@Composable
fun getNodeStateColor(node: Node): Color {

    when (node.type) {

        is KrillApp.Project -> {
            // Projects get a slightly brighter, warmer tint to stand out as organizational hubs
            return when (node.state) {
                NodeState.ERROR -> colorScheme.error
                NodeState.WARN -> Color(0xFFFF9800)
                else -> Color(0xFF2A6B5A) // Teal-green — distinct from the default purple/gray
            }
        }

        KrillApp.Server.Pin -> {
            val meta = (node.meta as PinMetaData)
            return if (meta.hardwareId.isNotEmpty() && meta.pinNumber > 0) {
                Color(meta.color?.toInt() ?: 0xFF000000.toInt())

            } else {
                colorScheme.primaryContainer
            }
        }


        else -> {

            return when (node.state) {

                NodeState.PAUSED -> colorScheme.primaryContainer // Default color
                NodeState.INFO -> Color(0xFF2196F3) // Blue - informational
                NodeState.WARN -> Color(0xFFFF9800) // Orange - warning
                NodeState.ERROR -> colorScheme.error // Red - error
                NodeState.PAIRING -> Color(0xFF435DEA)
                NodeState.NONE -> colorScheme.primaryContainer // Default color

                NodeState.EXECUTED -> Color(0xFF664CAF)
                NodeState.DELETING -> Color(0xFF1D1C1F)
                NodeState.CREATED -> Color(0xFF4C4A50)
                NodeState.USER_EDIT -> colorScheme.primaryContainer
                NodeState.SNAPSHOT_UPDATE -> Color(0xFF206664)
                NodeState.USER_SUBMIT -> colorScheme.primaryContainer
                NodeState.UNAUTHORISED -> Color(0xFF58575A)
                NodeState.EDITING ->  Color(0xFF435DEA)
            }
        }
    }
}


@Composable
fun NodeLabel(name: String) {


    val displayName = if (name.length > NODE_LABEL_MAX_LENGTH) name.take(NODE_LABEL_MAX_LENGTH) + "..." else name
    Box(
        modifier = Modifier
            .offset(y = NODE_LABEL_Y_OFFSET)
            .background(
                color = colorScheme.surface.copy(alpha = NODE_LABEL_BACKGROUND_ALPHA),
                shape = RoundedCornerShape(NODE_LABEL_CORNER_SHAPE)
            )
            .padding(horizontal = NODE_LABEL_HORIZONTAL_PADDING, vertical = NODE_LABEL_VERTICAL_PADDING)
    ) {
        Text(
            text = displayName,
            color = colorScheme.onSurface.copy(alpha = NODE_LABEL_TEXT_ALPHA),
            fontSize = NODE_LABEL_FONT_SIZE,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

