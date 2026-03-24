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
import krill.zone.shared.krillapp.executor.smtp.*
import krill.zone.shared.krillapp.executor.webhook.*
import krill.zone.shared.krillapp.project.*
import krill.zone.shared.krillapp.project.diagram.*
import krill.zone.shared.krillapp.project.journal.*
import krill.zone.shared.krillapp.project.tasklist.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.llm.*
import krill.zone.shared.krillapp.server.peer.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.krillapp.trigger.button.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*
import krill.zone.shared.node.*
import org.koin.core.component.*
import kotlin.time.*

/**
 * If you rename or move one of these make sure the first line of the json file matches the class structure and exists.
 */
@Serializable
sealed class MenuCommand : KrillApp() {

    @Serializable data object Update : MenuCommand()

    @Serializable data object Delete : MenuCommand()

    @Serializable data object Expand : MenuCommand()
    @Serializable data object Focus : MenuCommand()

}

/**
 * Map of every KrillApp parent to its direct children.
 * Key `null` holds the top-level KrillApp instances (direct subclasses of KrillApp).
 * Leaf nodes have no entry (or you can check `krillAppChildren[app].isNullOrEmpty()`).
 */

val krillAppChildren: Map<KrillApp?, List<KrillApp>> = mapOf(
    // Top-level
    null to listOf(
        KrillApp.Client, KrillApp.Server, KrillApp.Project,
        KrillApp.MQTT, KrillApp.DataPoint, KrillApp.Executor, KrillApp.Trigger
    ),

    // Server children
    KrillApp.Server to listOf(
        KrillApp.Server.Pin, KrillApp.Server.Peer,
        KrillApp.Server.LLM, KrillApp.Server.SerialDevice
    ),

    // Project children
    KrillApp.Project to listOf(
        KrillApp.Project.Diagram, KrillApp.Project.TaskList, KrillApp.Project.Journal
    ),

    // DataPoint children
    KrillApp.DataPoint to listOf(KrillApp.DataPoint.Filter, KrillApp.DataPoint.Graph),

    // DataPoint.Filter children
    KrillApp.DataPoint.Filter to listOf(
        KrillApp.DataPoint.Filter.DiscardAbove,
        KrillApp.DataPoint.Filter.DiscardBelow,
        KrillApp.DataPoint.Filter.Deadband,
        KrillApp.DataPoint.Filter.Debounce
    ),

    // Executor children
    KrillApp.Executor to listOf(
        KrillApp.Executor.LogicGate,
        KrillApp.Executor.OutgoingWebHook,
        KrillApp.Executor.Lambda,
        KrillApp.Executor.Calculation,
        KrillApp.Executor.Compute,
        KrillApp.Executor.SMTP
    ),

    // Trigger children
    KrillApp.Trigger to listOf(
        KrillApp.Trigger.Button,
        KrillApp.Trigger.CronTimer,
        KrillApp.Trigger.SilentAlarmMs,
        KrillApp.Trigger.HighThreshold,
        KrillApp.Trigger.LowThreshold,
        KrillApp.Trigger.IncomingWebHook
    ),
)

/** Flat list of every KrillApp instance (all levels). */
val allKrillApps: List<KrillApp> by lazy {
    fun collect(parent: KrillApp?): List<KrillApp> {
        val children = krillAppChildren[parent] ?: return emptyList()
        return children + children.flatMap { collect(it) }
    }
    collect(null)
}

/** Returns all descendants (children, grandchildren, etc.) of the given [app]. */
fun krillAppDescendants(app: KrillApp): List<KrillApp> {
    val children = krillAppChildren[app] ?: return emptyList()
    return children + children.flatMap { krillAppDescendants(it) }
}
private val krillAppLookupMap: Map<String, KrillApp> by lazy {
    fun hierarchicalName(app: KrillApp): String {
        val parent = krillAppChildren.entries.firstOrNull { (_, children) -> app in children }?.key
        return if (parent != null) "${hierarchicalName(parent)}.${app::class.simpleName}"
        else app::class.simpleName ?: ""
    }
    buildMap {
        for (app in allKrillApps) {
            val simpleName = app::class.simpleName ?: continue
            val hierName = hierarchicalName(app)
            put("KrillApp.$hierName", app)
            put(hierName, app)
            if (!containsKey(simpleName)) put(simpleName, app)
        }
    }
}

fun lookup(name: String): KrillApp? = krillAppLookupMap[name]
@Serializable
sealed class KrillApp(
    val meta: () -> (NodeMetaData) = { ServerMetaData() },
    @Transient val emit: (node: Node) -> Unit = { }
) {


    @Serializable
    data object Client : KrillApp(meta = { ClientMetaData(hostName) }, emit = { node ->
        object : KoinComponent {
            val processor: ClientProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {
        @Serializable
        data object About : KrillApp(meta = { ClientMetaData(hostName) }, emit = { node ->
            object : KoinComponent {
                val processor: ClientProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }

    @Serializable
    data object Server : KrillApp(meta = { ServerMetaData() }, emit = { node ->
        object : KoinComponent {
            val processor: ServerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {

        @Serializable
        data object Pin : KrillApp(meta = { PinMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: PinProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object Peer : KrillApp(meta = { ServerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: PeerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object LLM : KrillApp(meta = { LLMMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: LLMProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })


        @Serializable
        data object SerialDevice : KrillApp(
            meta = { SerialDeviceMetaData() },
            emit = { node ->
                object : KoinComponent {
                    val processor: SerialDeviceProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })


    }

    @Serializable
    data object Project : KrillApp(meta = { ProjectMetaData() }, emit = { node ->
        object : KoinComponent {
            val processor: ProjectProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {
        @Serializable
        data object Diagram : KrillApp(meta = { DiagramMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: DiagramProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object TaskList : KrillApp(meta = { TaskListMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TaskListProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object Journal : KrillApp(meta = { JournalMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: JournalProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }


    @Serializable
    data object MQTT : KrillApp(meta = { MqttMetaData() }, emit = { node ->
        object : KoinComponent {
            val processor: MqttProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    })


    @Serializable
    data object DataPoint : KrillApp(
        meta = { DataPointMetaData() },
        emit = { node ->
            object : KoinComponent {
                val processor: DataPointProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        }) {


        @Serializable
        data object Filter : KrillApp(meta = { FilterMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        }) {


            @Serializable
            data object DiscardAbove : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

            @Serializable
            data object DiscardBelow : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

            @Serializable
            data object Deadband : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

            @Serializable
            data object Debounce : KrillApp(meta = { FilterMetaData() }, emit = { node ->
                object : KoinComponent {
                    val processor: FilterProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            })

        }

        @Serializable
        data object Graph : KrillApp(
            meta = { GraphMetaData() },
            emit = { node ->
                object : KoinComponent {
                    val processor: GraphProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            }
        )
    }

    @Serializable
    data object Executor : KrillApp(emit = { node ->
        object : KoinComponent {
            val processor: ExecutorProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
        }.processor.post(node)
    }) {


        @Serializable
        data object LogicGate : KrillApp(meta = { LogicGateMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: LogicGateProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object OutgoingWebHook : KrillApp(meta = { WebHookOutMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: WebHookOutboundProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object Lambda : KrillApp(meta = { LambdaSourceMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: LambdaProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object Calculation : KrillApp(meta = { CalculationEngineNodeMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: CalculationProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object Compute : KrillApp(meta = { ComputeMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: ComputeProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object SMTP : KrillApp(meta = { SMTPMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: SMTPProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }

    @Serializable
    data object Trigger : KrillApp(
        meta = { TriggerMetaData() },

        emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        }
    ) {

        @Serializable
        data object Button : KrillApp(meta = { ButtonMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: ButtonProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })


        @Serializable
        data object CronTimer : KrillApp(
            meta = { CronMetaData(this::class.simpleName!!) },

            emit = { node ->
                object : KoinComponent {
                    val processor: CronProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
                }.processor.post(node)
            }
        )


        @Serializable
        data object SilentAlarmMs : KrillApp(meta = { TriggerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object HighThreshold : KrillApp(meta = { TriggerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object LowThreshold : KrillApp(meta = { TriggerMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: TriggerProcessor by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })

        @Serializable
        data object IncomingWebHook : KrillApp(meta = { IncomingWebHookMetaData() }, emit = { node ->
            object : KoinComponent {
                val processor: WebHookInboundProcessorInterface by inject(mode = LazyThreadSafetyMode.SYNCHRONIZED)
            }.processor.post(node)
        })
    }
}
