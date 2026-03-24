package krill.zone.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.app.krillapp.datapoint.*
import krill.zone.app.krillapp.datapoint.graph.*
import krill.zone.app.krillapp.executor.calculation.*
import krill.zone.app.krillapp.executor.compute.*
import krill.zone.app.krillapp.executor.cron.*
import krill.zone.app.krillapp.executor.lambda.*
import krill.zone.app.krillapp.executor.logicgate.*
import krill.zone.app.krillapp.executor.smtp.*
import krill.zone.app.krillapp.executor.webhook.*
import krill.zone.app.krillapp.mqtt.*
import krill.zone.app.krillapp.project.*
import krill.zone.app.krillapp.project.diagram.*
import krill.zone.app.krillapp.project.journal.*
import krill.zone.app.krillapp.project.tasklist.*
import krill.zone.app.krillapp.server.*
import krill.zone.app.krillapp.server.llm.*
import krill.zone.app.krillapp.server.pin.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*
import kotlin.uuid.*


@OptIn(ExperimentalUuidApi::class)
@Composable
fun NodeList(type: KrillApp, digitalOnly: Boolean = false, showTrash: Boolean = false, filterParent: String = "",callback: (String) -> Unit) {
    val nodeManager: ClientNodeManager = koinInject()

    val nodesState = nodeManager.nodes().filter { n -> n.type == type }

    var nodes = when (type) {
        KrillApp.DataPoint -> {
            if (digitalOnly) {
                nodesState.filter { n -> (n.meta as DataPointMetaData).dataType == DataType.DOUBLE }
            } else {
                nodesState
            }
        }
        else -> {
            nodesState
        }
    }

    if (! showTrash) {
        nodes = nodes.filter { n -> n.state != NodeState.DELETING }
    }

    if (filterParent.isNotEmpty()) {
        nodes = nodes.filter { n -> n.parent == filterParent }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SMALL)
    ) {
        Text(
            text = type.title(),
            style = MaterialTheme.typography.titleMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CommonLayout.CORNER_RADIUS_MEDIUM)
        ) {
            Column {
                if (nodes.isEmpty()) {
                    Text(
                        text = "No ${type.title()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(CommonLayout.PADDING_MEDIUM)
                    )
                }
                nodes.forEachIndexed { index, nodeItem ->
                    NodeRow(nodeItem, callback)
                    if (index < nodes.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = CommonLayout.PADDING_SMALL),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun SourceList(callback: (String) -> Unit) {
    val nodeManager: ClientNodeManager = koinInject()

    val tabs = listOf(
        KrillApp.DataPoint, KrillApp.Server.Pin, KrillApp.Executor.LogicGate
    )

    var selectedTabIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SMALL)
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(tab.title()) }
                )
            }
        }

        val selectedTab = tabs[selectedTabIndex]
        val nodes = nodeManager.nodes().filter { node -> node.type == selectedTab && node.state != NodeState.DELETING }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CommonLayout.CORNER_RADIUS_MEDIUM)
        ) {
            Column {
                if (nodes.isEmpty()) {
                    Text(
                        text = "No ${selectedTab.title()} available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(CommonLayout.PADDING_MEDIUM)
                    )
                }
                nodes.forEachIndexed { index, nodeItem ->
                    NodeRow(nodeItem, callback)
                    if (index < nodes.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = CommonLayout.PADDING_SMALL),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NodeRow(node: Node, callback: (String) -> Unit) {


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Add data point to formula
                callback(node.id)
            }
            .padding(CommonLayout.PADDING_SMALL),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CommonLayout.SPACING_SMALL)
    ) {

        when (node.type) {
            KrillApp.Server, KrillApp.Server.Peer -> {
                ServerRow(node.id) {
                    callback(node.id)
                }
            }

            KrillApp.DataPoint -> {
                DataPointRow(node.id, false)
            }



            KrillApp.Executor.Calculation -> {
                CalculationRow(node.id)
            }
            KrillApp.Executor.Compute -> {
                ComputeRow(node.id)
            }
            KrillApp.Executor.Lambda -> {
                LambdaRow(node.id)
            }
            KrillApp.Executor.LogicGate -> {
                LogicGateRow(node.id)
            }
            KrillApp.Executor.OutgoingWebHook -> {
                OutgoingWebHookRow(node.id)
            }
            KrillApp.Executor.SMTP -> {
                SMTPRow(node.id)
            }
            KrillApp.Server.Pin -> {
                PinRow(node.id)
            }
            KrillApp.Trigger.CronTimer -> {
                CronRow(node.id)
            }

            KrillApp.MQTT -> {
                MqttRow(node.id)
            }
            KrillApp.Server.LLM -> {
                LlmRow(node.id)
            }
            KrillApp.DataPoint.Graph -> {
                GraphRow(node.id)
            }
            KrillApp.Project -> {
                ProjectRow(node.id)
            }
            KrillApp.Project.Diagram -> {
                DiagramRow(node.id)
            }
            KrillApp.Project.TaskList -> {
                TaskListRow(node.id)
            }
            KrillApp.Project.Journal -> {
                JournalRow(node.id)
            }
            else -> {}
        }



    }
}
