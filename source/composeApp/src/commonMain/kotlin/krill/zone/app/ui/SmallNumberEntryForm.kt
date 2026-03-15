package krill.zone.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.input.*
import krill.composeapp.generated.resources.*
import krill.zone.app.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.datapoint.filter.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.jetbrains.compose.resources.*
import org.koin.compose.*
import kotlin.time.*


@OptIn(ExperimentalTime::class)
@Composable
fun ManualEntry(node: Node, value: Double, showSaveButton: Boolean , callback: (Double) -> Unit) {

    val screenCore: ScreenCore = koinInject()
    val nodeManager: ClientNodeManager = koinInject()
    val entry = remember { mutableStateOf(value) }
    LaunchedEffect(entry.value) {
        callback(entry.value)
    }

    // Compact inline display for ring menu - no offset, no background
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CommonLayout.PADDING_FORM_SMALL),
        modifier = Modifier.wrapContentWidth()
    ) {
        // Value input with save icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SMALL)
        ) {

            BasicTextField(
                value = entry.value.toString(),
                onValueChange = { s ->

                    try {
                        entry.value = s.toDouble()
                        callback(entry.value)
                    } catch (_: NumberFormatException) {
                        // Keep existing value if invalid
                    }

                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .width(CommonLayout.NUMBER_FORM_WIDTH)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(CommonLayout.CORNER_RADIUS_SMALL)
                    )
                    .border(
                        width = CommonLayout.BORDER_THIN,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(CommonLayout.CORNER_RADIUS_SMALL)
                    )
                    .padding(horizontal = CommonLayout.PADDING_FORM_MEDIUM, vertical = CommonLayout.PADDING_SMALL)
            )
            // Compact save icon - larger and minimal padding
            if (showSaveButton) {
                Box(
                    modifier = Modifier
                        .size(CommonLayout.GATE_ICON_SIZE)
                        .clickable {
                            when (node.meta) {
                                is DataPointMetaData -> {

                                    val snapshot = Snapshot(
                                        timestamp = Clock.System.now().toEpochMilliseconds(), value = entry.value
                                    )

                                    //results in a post to the server which goes to the data processor
                                    nodeManager.postSnapshot(node, snapshot)
                                }

                                is TriggerMetaData -> {
                                    val meta = (node.meta as TriggerMetaData).copy(value = entry.value)
                                    nodeManager.updateMetaData(node, meta)
                                }

                                is FilterMetaData -> {
                                    val meta = (node.meta as FilterMetaData).copy(value = entry.value)
                                    nodeManager.updateMetaData(node, meta)
                                }

                                else -> {
                                    throw Exception("unsupported meta gateType")
                                }
                            }



                            screenCore.selectNode(null)


                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.floppy_disk_duotone_regular_full),
                        contentDescription = stringResource(Res.string.save_content_description),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.size(CommonLayout.ICON_SIZE_STANDARD)
                    )
                }
            }
        }
    }


}