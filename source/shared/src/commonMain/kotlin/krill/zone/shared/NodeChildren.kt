package krill.zone.shared


import krill.zone.shared.krillapp.client.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*

class NodeChildren(private val nodeManager: ClientNodeManager) {


    fun serverCapabilities(node: Node): Set<KrillApp> {
        val set = mutableSetOf<KrillApp>()
        if (node.state == NodeState.ERROR || node.state == NodeState.WARN) {
            return setOf(
                MenuCommand.Update,
            )
        }
        set.addAll(
            listOf(
                MenuCommand.Update,
                KrillApp.Server.Peer,
                KrillApp.Server.LLM,
                KrillApp.Project,


            )
        )
        when (node.type) {


            KrillApp.Project -> {
                set.addAll(listOf(KrillApp.DataPoint,
                    KrillApp.Server.SerialDevice,
                    KrillApp.Trigger.Button,
                    KrillApp.Trigger.CronTimer,
                    KrillApp.Trigger.IncomingWebHook,
                    KrillApp.MQTT,
                    KrillApp.Server.Pin))
                nodeManager.readNodeStateOrNull(node.host).value?.let { host ->
                    val meta = host.meta as ServerMetaData
                    if (meta.platform == Platform.RASPBERRY_PI) {
                        set.add(KrillApp.Server.Pin)
                    }
                }
            }

            else -> {}
        }


        set.remove(MenuCommand.Delete)
        return set
    }

    fun triggerCapabilities(): Set<KrillApp> {
        return setOf(
            MenuCommand.Update,


            ).plus(dataExecutors())
    }

    fun dataExecutors(): Set<KrillApp> {
        return setOf(
            KrillApp.Executor.LogicGate,
            KrillApp.Executor.OutgoingWebHook,
            KrillApp.Executor.Lambda,
            KrillApp.Executor.LogicGate,
            KrillApp.Executor.Calculation,
            KrillApp.Executor.Compute,
            KrillApp.Executor.SMTP,
            KrillApp.MQTT,
            KrillApp.Project.Camera,
            KrillApp.Server.Backup
        )
    }

    fun load(node: Node): List<KrillApp> {

        //give chance to delete broken nodes.
        if (!nodeManager.nodeAvailable(node.id)) {
            return listOf(MenuCommand.Delete)
        }

        val state = nodeManager.readNodeState(node.id).value
        val host = nodeManager.readNodeState(state.host).value
        val parent = nodeManager.readNodeState(state.parent).value
        val distinct = mutableListOf<KrillApp>()

        when (state.type) {

            KrillApp.Server -> {
                distinct.addAll(serverCapabilities(state))
            }

            KrillApp.Client -> {
                val meta = state.meta as ClientMetaData
                if (meta.ftue) {
                    distinct.clear()
                } else {
                    distinct.add(KrillApp.Client.About)
                    distinct.add(KrillApp.Server)
                    distinct.add(KrillApp.Server.Peer)

                    nodeManager.nodes().map { state -> state.type }.distinct().minus(KrillApp.Server.Peer)
                        .forEach { type ->
                            distinct.add(type)
                        }
                }
                distinct.remove(KrillApp.Client)
                distinct.remove(KrillApp.Trigger.Button)
                distinct.remove(MenuCommand.Update)
                distinct.remove(MenuCommand.Delete)


            }

            KrillApp.Server.Pin -> {
                val meta = state.meta as PinMetaData


                if (meta.pinNumber > 0) {
                    distinct.addAll(triggerCapabilities())
                }

            }

            KrillApp.Server.SerialDevice -> {
                distinct.remove(KrillApp.DataPoint)
            }

            KrillApp.DataPoint -> {
                val meta = state.meta as DataPointMetaData
                distinct.add(MenuCommand.Update)


                if (meta.dataType == DataType.DIGITAL) {
                    distinct.addAll(triggerCapabilities())
                    distinct.add(KrillApp.DataPoint.Graph)
                }

                if (meta.dataType == DataType.DOUBLE) {

                    distinct.add(KrillApp.DataPoint.Filter)
                    distinct.add(KrillApp.Trigger)
                    distinct.addAll(dataExecutors())
                    distinct.add(KrillApp.DataPoint.Graph)
                }

                if (meta.dataType == DataType.COLOR) {
                    distinct.add(KrillApp.Trigger)
                    distinct.addAll(dataExecutors())
                    distinct.add(KrillApp.DataPoint.Graph)
                }
            }

            KrillApp.DataPoint.Graph -> {
                // Graph is read-only, no child nodes
                distinct.remove(MenuCommand.Update)
            }

            KrillApp.Trigger.CronTimer -> {
                val meta = state.meta as CronMetaData
                if (meta.expression.isEmpty()) {
                    return emptyList()
                } else {
                    distinct.addAll(triggerCapabilities())
                }


            }


            KrillApp.Trigger.IncomingWebHook -> {
                val meta = state.meta as IncomingWebHookMetaData
                if (meta.path.isEmpty()) {
                    return emptyList()
                } else {
                    distinct.addAll(triggerCapabilities())
                }


            }

            KrillApp.Trigger.SilentAlarmMs,

            KrillApp.Trigger.HighThreshold,

            KrillApp.Trigger.LowThreshold,

            KrillApp.Trigger.Color -> {

                val hostmeta = host.meta as ServerMetaData

                distinct.addAll(triggerCapabilities())


                if (hostmeta.platform != Platform.RASPBERRY_PI) {
                    distinct.remove(KrillApp.Server.Pin)

                }


            }

            KrillApp.DataPoint.Filter -> {
                val pm = parent.meta as DataPointMetaData
                distinct.add(MenuCommand.Delete)
                if (pm.dataType == DataType.DOUBLE || pm.dataType != DataType.DIGITAL) {
                    distinct.remove(KrillApp.Trigger.HighThreshold)
                    distinct.remove(KrillApp.Trigger.LowThreshold)
                    distinct.add(KrillApp.DataPoint.Filter.DiscardBelow)
                    distinct.add(KrillApp.DataPoint.Filter.DiscardAbove)
                    distinct.add(KrillApp.DataPoint.Filter.Deadband)
                    distinct.add(KrillApp.DataPoint.Filter.Debounce)

                }

                distinct.remove(MenuCommand.Update)

            }

            KrillApp.Trigger -> {
                distinct.addAll(
                    listOf(
                        MenuCommand.Delete,
                        KrillApp.Trigger.HighThreshold,
                        KrillApp.Trigger.LowThreshold,
                        KrillApp.Trigger.SilentAlarmMs,
                        KrillApp.Trigger.Color
                    ).plus(dataExecutors())
                )
            }

            KrillApp.Trigger.Button -> {
                distinct.addAll(triggerCapabilities())
            }

            KrillApp.Executor, KrillApp.DataPoint.Filter.DiscardAbove, KrillApp.DataPoint.Filter.DiscardBelow, KrillApp.DataPoint.Filter.Deadband, KrillApp.DataPoint.Filter.Debounce -> {
                distinct.remove(MenuCommand.Update)

            }

            KrillApp.Executor.LogicGate -> {
                distinct.addAll(triggerCapabilities())
            }

            KrillApp.Project -> {
                distinct.addAll(serverCapabilities(state))
                distinct.add(KrillApp.Project)
                distinct.add(KrillApp.Project.Diagram)
                distinct.add(KrillApp.Project.TaskList)
                distinct.add(KrillApp.Project.Journal)
                distinct.add(KrillApp.DataPoint.Graph)
                distinct.add(KrillApp.Executor.LogicGate)
                distinct.add(KrillApp.Executor.Lambda)
                val meta = host.meta as ServerMetaData
                if (meta.platform == Platform.RASPBERRY_PI) {
                    distinct.add(KrillApp.Server.Pin)
                    distinct.add(KrillApp.Project.Camera)
                }
            }

            KrillApp.Project.Diagram -> {

            }

            KrillApp.Executor.Lambda -> {
                distinct.add(MenuCommand.Update)
            }

            else -> {}
        }


        return distinct.distinct()


    }


}