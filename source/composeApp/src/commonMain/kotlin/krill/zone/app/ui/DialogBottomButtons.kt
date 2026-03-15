package krill.zone.app.ui


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import krill.composeapp.generated.resources.*
import krill.zone.app.*
import krill.zone.app.util.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.jetbrains.compose.resources.*
import org.koin.compose.*


@Composable
fun DialogBottomButtons() {
    val screenCore: ScreenCore = koinInject()
    val nodeManager: ClientNodeManager = koinInject()
    val selectedNodeId =  nodeManager.selectedNodeId.collectAsState()
    val nodeId = selectedNodeId.value ?: ""
    val selectedNode : StateFlow<Node>? = if (nodeManager.nodeAvailable(nodeId)) { nodeManager.readNodeState(nodeId) } else { null  }

    // Animation state for clipboard button
    var isCopied by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isCopied) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val buttonColor by animateColorAsState(
        targetValue = if (isCopied)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300)
    )

    // Reset animation after delay
    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(800)
            isCopied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(CommonLayout.PADDING_LARGE),
        horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
    ) {

        if (! isMobile && selectedNode?.value?.type != KrillApp.Client) {
            IconButton(
                onClick = {
                    copyToClipboard(nodeId )
                    isCopied = true
                }
            ) {
                EmojiText(
                    text = if (isCopied) "✓" else "📋",
                    style = MaterialTheme.typography.titleMedium,
                    color = buttonColor,
                    modifier = Modifier.scale(scale)
                )
            }
            val id = if (nodeId.contains(":")) { nodeId.split(":").last() } else { nodeId}
            Text(stringResource(Res.string.node_id_label, id))
            Spacer(Modifier.weight(1f, true))
        }
        TextButton(
            onClick = {
                screenCore.reset()

            }
        ) {
            Text(stringResource(Res.string.cancel))
        }

        Spacer(modifier = Modifier.width(CommonLayout.SPACING_SMALL))
        if (selectedNode?.value?.type !is KrillApp.Client) {
            SaveNodeButton()
        }

    }
}
