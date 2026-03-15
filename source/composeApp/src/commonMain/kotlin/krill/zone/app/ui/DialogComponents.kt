package krill.zone.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.*
import kotlinx.datetime.*
import krill.zone.app.*
import krill.zone.shared.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*
import kotlin.time.*
import kotlin.time.Instant

/**
 * Displays error message from the selected node's metadata if present.
 * Shows a red error row with an error icon when the node has an error state.
 */
@Composable
fun ErrorMessageRow() {


    val nodeManager: ClientNodeManager = koinInject()
    val selectedNodeId =  nodeManager.selectedNodeId.collectAsState()
    val nodeId = selectedNodeId.value ?: return
    if (!nodeManager.nodeAvailable(nodeId)) return

    val node by nodeManager.readNodeState(nodeId).collectAsState()

    // Only show if node is in error state and has an error message
    if (node.state == NodeState.ERROR && node.meta.error.isNotBlank()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CommonLayout.PADDING_LARGE, vertical = CommonLayout.PADDING_SMALL),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CommonLayout.PADDING_MEDIUM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_MEDIUM)
            ) {
                Text(
                    text = "⚠️ ${node.meta.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}


@Composable
fun NodeEditorContainer(viewMode: ViewMode, content: @Composable () -> Unit) {

    if (viewMode == ViewMode.ROW) {
        content()
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.systemBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row { NodeHeader() }
            HorizontalDivider()
            // Scrollable content area with proper constraints
            val isMobileOrView = isMobile || viewMode == ViewMode.VIEW
            val contentWeight = if (isMobileOrView) 0.9f else 0.6f
            val contentWidth = if (isMobileOrView) 0.95f else 0.82f

            Box(
                modifier = Modifier
                    .weight(contentWeight)
                    .fillMaxWidth(contentWidth)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(CommonLayout.PADDING_LARGE),
                    verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_LARGE),
                    horizontalAlignment = Alignment.Start,
                ) {
                    content()
                }
            }
            if (viewMode == ViewMode.EDIT) {
                HorizontalDivider()
                ErrorMessageRow()
                DialogBottomButtons()
            }
        }
    }
}





@OptIn(ExperimentalTime::class)
@Composable
fun TimestampText(ms: Long, style: TextStyle = LocalTextStyle.current) {
    Text(
        run {

            val ldt = Instant.fromEpochMilliseconds(ms)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            val y = ldt.year
            val m = ldt.month.number.toString().padStart(2, '0')
            val d = ldt.day.toString().padStart(2, '0')
            val hh = ldt.hour.toString().padStart(2, '0')
            val mm = ldt.minute.toString().padStart(2, '0')
            "⏱️ $y-$m-$d $hh:$mm"
        },
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
        style = style
    )
}

@Composable
fun <T> OptionSelector(radioOptions: List<T>, selectedOption: T?, onSelected: (T) -> Unit) {

    Column(modifier = Modifier.selectableGroup()) {
        radioOptions.forEach { type ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(CommonLayout.BUTTON_HEIGHT_LARGE)
                    .selectable(
                        selected = (type == selectedOption),
                        onClick = {
                            onSelected(type)
                            // selectedOption = gateType

                        },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = CommonLayout.PADDING_LARGE),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (type == selectedOption),
                    onClick = null // null recommended for accessibility with screen readers
                )
                Text(
                    text = type.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = CommonLayout.PADDING_LARGE)
                )
            }
        }
    }

}

@Composable
fun <T> HorizontalOptionSelector(radioOptions: List<T>, selectedOption: T?, onSelected: (T) -> Unit) {
    Row(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_LARGE)
    ) {
        radioOptions.forEach { type ->
            Row(
                Modifier
                    .selectable(
                        selected = (type == selectedOption),
                        onClick = { onSelected(type) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = CommonLayout.PADDING_SMALL),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (type == selectedOption),
                    onClick = null
                )
                Text(
                    text = type.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = CommonLayout.PADDING_EXTRA_SMALL)
                )
            }
        }
    }
}

