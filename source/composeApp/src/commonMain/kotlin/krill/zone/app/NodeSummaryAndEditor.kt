
package krill.zone.app


import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.*
import krill.zone.app.krillapp.client.about.*
import krill.zone.app.krillapp.datapoint.*
import krill.zone.app.krillapp.datapoint.filter.*
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
import krill.zone.app.krillapp.project.camera.*
import krill.zone.app.krillapp.project.diagram.*
import krill.zone.app.krillapp.project.journal.*
import krill.zone.app.krillapp.project.tasklist.*
import krill.zone.app.krillapp.server.*
import krill.zone.app.krillapp.server.backup.*
import krill.zone.app.krillapp.server.llm.*
import krill.zone.app.krillapp.server.peer.*
import krill.zone.app.krillapp.server.pin.*
import krill.zone.app.krillapp.server.serialdevice.*
import krill.zone.app.krillapp.trigger.*
import krill.zone.app.krillapp.trigger.color.*
import krill.zone.app.krillapp.trigger.incomingwebhook.*
import krill.zone.app.ui.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.executor.calculation.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.compose.*

enum class ViewMode {
    EDIT, VIEW, ROW
}


@Composable
fun NodeSummaryAndEditor(node: Node, viewMode: ViewMode) {
    val nodeManager: ClientNodeManager = koinInject()
    val nodeFlow = remember(node.id) {
        if (nodeManager.nodeAvailable(node.id)) nodeManager.readNodeState(node.id) else MutableStateFlow(node)
    }
    val nodeState = nodeFlow.collectAsState()

    nodeState.value.let { n ->
        // Key on both node ID and type to force full recreation when switching between
        // different node types. This prevents child composables from recomposing with
        // stale state when the selected node changes to a different type.
        key(n.id, n.type) {
            NodeEditorContainer(viewMode) {
                when (n.type) {
                    KrillApp.Server -> {
                        when (viewMode) {
                            ViewMode.EDIT -> EditServer(n)
                            ViewMode.VIEW -> ExpandServer()
                            ViewMode.ROW -> ServerRow(n.id) {}
                        }

                    }

                    KrillApp.DataPoint -> {
                        if (viewMode == ViewMode.ROW) {
                            DataPointView()
                        } else {
                            EditDataPoint(n)
                        }
                    }

                    KrillApp.Server.Pin -> {
                        if (viewMode == ViewMode.ROW) {
                            val meta = n.meta as PinMetaData
                            if (meta.pinNumber == 0) {
                                Row {
                                    Text("GPIO Pin Not Configured")
                                }
                            } else {
                                PinRowView(n)
                            }
                        } else {
                            EditPin(n)
                        }
                    }

                    KrillApp.Executor.Calculation -> {
                        if (viewMode == ViewMode.ROW) {
                            FormulaDisplay(n.meta as CalculationEngineNodeMetaData)
                        } else {
                            EditCalc()
                        }
                    }

                    KrillApp.Server.SerialDevice -> {
                        if (viewMode == ViewMode.ROW) {
                            SerialDeviceRow(n.id)
                        } else {
                            EditSerialDevice(n)
                        }
                    }

                    KrillApp.Executor.Compute -> {
                        if (viewMode == ViewMode.ROW) {
                            ComputeConfigDisplay(n)
                        } else {
                            EditCompute(n)
                        }
                    }

                    KrillApp.Trigger.CronTimer -> {
                        if (viewMode == ViewMode.ROW) {
                            CronView(n)
                        } else {
                            EditCron(n)
                        }
                    }

                    KrillApp.Executor.OutgoingWebHook -> {
                        if (viewMode == ViewMode.ROW) {
                            OutboundWebhookConfigDisplay(n)
                        } else {
                            EditOutboundWebHook(n)
                        }
                    }

                    KrillApp.Executor.SMTP -> {
                        if (viewMode == ViewMode.ROW) {
                            SMTPConfigDisplay(n)
                        } else {
                            EditSMTP(n)
                        }
                    }

                    KrillApp.Trigger.IncomingWebHook -> {
                        if (viewMode == ViewMode.ROW) {
                            IncomingWebhookConfigDisplay(n)
                        } else {
                            EditIncomingWebHook(n)
                        }
                    }

                    KrillApp.Executor.Lambda -> {
                        if (viewMode == ViewMode.ROW) {
                            LambdaConfigDisplay(n)
                        } else {
                            EditLambda(n)
                        }
                    }

                    KrillApp.Executor.LogicGate -> {
                        if (viewMode == ViewMode.ROW) {
                            LogicGateConfigDisplay(n)
                        } else {
                            EditLogicGate()
                        }
                    }

                    KrillApp.MQTT -> {
                        if (viewMode == ViewMode.ROW) {
                            MqttConfigDisplay(n)
                        } else {
                            EditMQTT(n)
                        }
                    }

                    KrillApp.Server.Peer -> {
                        if (viewMode == ViewMode.ROW) {
                            PeerView()
                        } else {
                            EditPeer()
                        }
                    }

                    KrillApp.Server.LLM -> {
                        if (viewMode == ViewMode.ROW) {
                            LlmRow(n.id)
                        } else {
                            EditLLM(n)
                        }
                    }


                    KrillApp.DataPoint.Filter.Deadband, KrillApp.DataPoint.Filter.Debounce, KrillApp.DataPoint.Filter.DiscardAbove, KrillApp.DataPoint.Filter.DiscardBelow -> {
                        EditFilter(n)
                    }

                    KrillApp.DataPoint.Graph -> {
                        if (viewMode == ViewMode.ROW) {
                            GraphRow(n.id)
                        } else {
                            EditGraph(n)
                        }
                    }

                    KrillApp.Project -> {
                        if (viewMode == ViewMode.ROW) {
                            ProjectRow(n.id)
                        } else {
                            EditProject(n)
                        }
                    }

                    KrillApp.Project.Diagram -> {
                        when (viewMode) {
                            ViewMode.EDIT -> {
                                EditDiagram(n)
                            }

                            ViewMode.VIEW -> {
                                DiagramScreen(n)
                            }

                            ViewMode.ROW -> {
                                DiagramRow(n)
                            }
                        }


                    }

                    KrillApp.Project.TaskList -> {
                        if (viewMode == ViewMode.ROW) {
                            TaskListRow(n.id)
                        } else {
                            EditTaskList(n)
                        }
                    }

                    KrillApp.Project.Journal -> {
                        if (viewMode == ViewMode.ROW) {
                            JournalRow(n.id)
                        } else {
                            EditJournal(n)
                        }
                    }

                    KrillApp.Project.Camera -> {
                        when (viewMode) {
                            ViewMode.EDIT -> EditCamera(n)
                            ViewMode.VIEW -> CameraView(n)
                            ViewMode.ROW -> CameraRow(n.id)
                        }
                    }


                    KrillApp.Trigger.HighThreshold, KrillApp.Trigger.LowThreshold, KrillApp.Trigger.SilentAlarmMs -> {
                        when (viewMode) {
                            ViewMode.EDIT -> {
                                EditTrigger(n, false)
                            }

                            ViewMode.VIEW -> {
                                EditTrigger(n, false)
                            }

                            ViewMode.ROW -> {
                                EditTrigger(n, true)
                            }
                        }

                    }

                    KrillApp.Trigger.Color -> {
                        when (viewMode) {
                            ViewMode.EDIT -> EditColorTrigger(n, false)
                            ViewMode.VIEW -> EditColorTrigger(n, false)
                            ViewMode.ROW -> EditColorTrigger(n, true)
                        }
                    }

                    KrillApp.Server.Backup -> {
                        when (viewMode) {
                            ViewMode.EDIT -> EditBackup(n)
                            ViewMode.VIEW -> BackupView(n)
                            ViewMode.ROW -> BackupRow(n.id)
                        }
                    }

                    KrillApp.Client.About -> {
                        AboutScreen()
                    }

                    KrillApp.Client -> {
                        Text("Manage node types that exist in your swarm or connect to external servers:")
                    }

                    else -> {}
                }
            }
        }
    }

}