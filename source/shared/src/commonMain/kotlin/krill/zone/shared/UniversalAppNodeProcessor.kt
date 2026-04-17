package krill.zone.shared

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.datapoint.filter.*
import krill.zone.shared.krillapp.datapoint.graph.*
import krill.zone.shared.krillapp.executor.*
import krill.zone.shared.krillapp.executor.calculation.*
import krill.zone.shared.krillapp.executor.compute.*
import krill.zone.shared.krillapp.executor.lambda.*
import krill.zone.shared.krillapp.executor.logicgate.*
import krill.zone.shared.krillapp.executor.mqtt.*
import krill.zone.shared.krillapp.executor.smtp.*
import krill.zone.shared.krillapp.executor.webhook.*
import krill.zone.shared.krillapp.project.*
import krill.zone.shared.krillapp.project.camera.*
import krill.zone.shared.krillapp.project.diagram.*
import krill.zone.shared.krillapp.project.journal.*
import krill.zone.shared.krillapp.project.tasklist.*
import krill.zone.shared.krillapp.server.backup.*
import krill.zone.shared.krillapp.server.llm.*
import krill.zone.shared.krillapp.server.peer.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.krillapp.trigger.button.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*
import kotlin.time.*
import kotlin.uuid.*

class

UniversalAppNodeProcessor(

    private val nodeManager: ClientNodeManager,
    private val observer: NodeObserver,
    private val scope: CoroutineScope
) : CronProcessor,
    DataPointProcessorInterface,
    CalculationProcessor,
    ComputeProcessor,
    LambdaProcessorInterface,
    PinProcessor,
    TriggerProcessor,
    ExecutorProcessorInterface,
    WebHookInboundProcessorInterface,
    WebHookOutboundProcessorInterface,
    SerialDeviceProcessor,
    FilterProcessorInterface,
    ButtonProcessor,
    LogicGateProcessor,
    MqttProcessor,
    PeerProcessor,
    ProjectProcessor,
    GraphProcessorInterface,
    DiagramProcessor,
    TaskListProcessor,
    JournalProcessor,
    LLMProcessor,
    SMTPProcessorInterface,
    CameraProcessorInterface,
    BackupProcessorInterface {
    private val logger = Logger.withTag(this::class.getFullName())

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    override fun post(node: Node) {



        scope.launch {
            when (node.state) {
                NodeState.PAUSED -> {}
                NodeState.INFO -> {}
                NodeState.WARN -> {}
                NodeState.SEVERE -> {}
                NodeState.ERROR -> {}
                NodeState.PAIRING -> {}
                NodeState.NONE -> { }

                NodeState.USER_EDIT -> {
                    logger.i { "${node.details()} user edit node processor posted " }
                }
                NodeState.UNAUTHORISED -> {}
                NodeState.EXECUTED -> {

                    executeVisuals(node)

                }

                NodeState.DELETING -> {

                        observer.remove(node.id)
                        nodeManager.remove(node.id)

                }


                NodeState.SNAPSHOT_UPDATE, NodeState.USER_SUBMIT, NodeState.CREATED -> {

                    showActivity(node)
                }

                NodeState.EDITING -> {}
            }

        }
    }

    private fun executeVisuals(node: Node) {
        when (node.type) {
            is
            KrillApp.Executor.Calculation,
            KrillApp.Executor.Compute,
            KrillApp.Executor.LogicGate,
            KrillApp.Executor.Lambda,
            KrillApp.Trigger.IncomingWebHook,
            KrillApp.Server.SerialDevice,

            KrillApp.Executor.SMTP,
            KrillApp.MQTT -> {

                logger.d { "noting interaction of ${node.details()} --> ${(node.meta as TargetingNodeMetaData).targets} " }
                logger.d { "noting interaction of ${node.details()} <-- ${(node.meta as TargetingNodeMetaData).sources} " }
                nodeManager.addInteraction(node)
                showActivity(node)
            }

            is KrillApp.Trigger.Button -> {
                showActivity(node)

            }

            is KrillApp.DataPoint -> {


                showActivity(node)
            }


            else -> {
                showActivity(node)
            }
        }
    }

    override suspend fun process(node: Node): Boolean {
        return true
    }




    private fun showActivity(node: Node) {
        if (nodeManager.selectedNodeId.value?.isNotEmpty() == true) { return }
        if (node.state != NodeState.ERROR && node.state != NodeState.DELETING) {
            scope.launch {

                nodeManager.startPairing(node)
                delay(1500)
                try {
                    val update = nodeManager.readNodeState(node.id)
                    if (update.value.state != NodeState.ERROR && update.value.state != NodeState.DELETING) {
                        nodeManager.reset(node)
                    }
                } catch (e: Exception) {
                    logger.i { "Failed to reset node ${node.id}: ${e.message}" }
                }
            }
        }
    }
}


