@file:OptIn(ExperimentalTime::class)

package krill.zone.shared

import kotlinx.serialization.*
import krill.zone.shared.krillapp.client.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.datapoint.filter.*
import krill.zone.shared.krillapp.datapoint.graph.*
import krill.zone.shared.krillapp.executor.*
import krill.zone.shared.krillapp.executor.calculation.*
import krill.zone.shared.krillapp.executor.compute.*
import krill.zone.shared.krillapp.executor.lambda.*
import krill.zone.shared.krillapp.executor.logicgate.*
import krill.zone.shared.krillapp.executor.mqtt.*
import krill.zone.shared.krillapp.executor.webhook.*
import krill.zone.shared.krillapp.project.*
import krill.zone.shared.krillapp.project.diagram.*
import krill.zone.shared.krillapp.project.journal.*
import krill.zone.shared.krillapp.project.tasklist.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.peer.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.krillapp.trigger.button.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*
import krill.zone.shared.ksp.*
import krill.zone.shared.node.*
import org.koin.core.component.*
import kotlin.time.*

/**
 * If you rename or move one of these make sure the first line of the json file matches the class structure and exists.
 */
sealed class MenuCommand : KrillApp() {

    data object Update : MenuCommand()

    data object Delete : MenuCommand()

    data object Expand : MenuCommand()
    data object Focus : MenuCommand()
    data object About : MenuCommand()


}

@Serializable
sealed class KrillApp(
    val meta: () -> (NodeMetaData) = { ServerMetaData() },
    @Transient val emit: (node: Node) -> Unit = { }
) {


    @Krill
    data object Client : KrillApp(meta = { ClientMetaData(hostName) }, emit = { node ->
        object : KoinComponent {
            val processor: ClientProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    })

    @Krill
    data object Server : KrillApp(meta = { ServerMetaData() }, emit = { node ->
        object : KoinComponent {
            val processor: ServerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {

        @Krill
        data object Pin : KrillApp(meta = { PinMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: PinProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object Peer : KrillApp(meta = { ServerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: PeerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })


        @Krill
        data object SerialDevice : KrillApp(
            meta = { SerialDeviceMetaData() },
            emit = { node ->
                object : KoinComponent {
                    val processor: SerialDeviceProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })


    }

    @Krill
    data object Project : KrillApp(meta = { ProjectMetaData() }, emit = { node ->
        object : KoinComponent {
            val processor: ProjectProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {
        @Krill
        data object Diagram : KrillApp(meta = { DiagramMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: DiagramProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object TaskList : KrillApp(meta = { TaskListMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TaskListProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object Journal : KrillApp(meta = { JournalMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: JournalProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }


    @Krill
    data object MQTT : KrillApp(meta = { MqttMetaData() }, emit = { node ->
        object : KoinComponent {
            val processor: MqttProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    })


    @Krill
    data object DataPoint : KrillApp(
        meta = { DataPointMetaData() },
        emit = { node ->
            object : KoinComponent {
                val processor: DataPointProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        }) {


        @Krill
        data object Filter : KrillApp(meta = { FilterMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        }) {


            @Krill
            data object DiscardAbove : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

            @Krill
            data object DiscardBelow : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

            @Krill
            data object Deadband : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

            @Krill
            data object Debounce : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

        }

        @Krill
        data object Graph : KrillApp(
            meta = { GraphMetaData() },
            emit = { node ->
                object : KoinComponent {
                    val processor: GraphProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            }
        )
    }

    @Krill
    data object Executor : KrillApp(emit = { node ->
        object : KoinComponent {
            val processor: ExecutorProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {


        @Krill
        data object LogicGate : KrillApp(meta = { LogicGateMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: LogicGateProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object OutgoingWebHook : KrillApp(meta = { WebHookOutMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: WebHookOutboundProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object Lambda : KrillApp(meta = { LambdaSourceMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: LambdaProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object Calculation : KrillApp(meta = { CalculationEngineNodeMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: CalculationProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object Compute : KrillApp(meta = { ComputeMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: ComputeProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }

    @Krill
    data object Trigger : KrillApp(
        meta = { TriggerMetaData() },

        emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        }
    ) {

        @Krill
        data object Button : KrillApp(meta = { ButtonMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: ButtonProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })


        @Krill
        data object CronTimer : KrillApp(
            meta = { CronMetaData(this::class.simpleName!!) },

            emit = { node ->
                object : KoinComponent {
                    val processor: CronProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            }
        )


        @Krill
        data object SilentAlarmMs : KrillApp(meta = { TriggerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object HighThreshold : KrillApp(meta = { TriggerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object LowThreshold : KrillApp(meta = { TriggerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Krill
        data object IncomingWebHook : KrillApp(meta = { IncomingWebHookMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: WebHookInboundProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }
}
