package krill.zone.shared.di

import krill.zone.shared.*
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
import krill.zone.shared.krillapp.server.llm.*
import krill.zone.shared.krillapp.server.peer.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.krillapp.spacer.*
import krill.zone.shared.krillapp.trigger.*
import krill.zone.shared.krillapp.trigger.button.*
import krill.zone.shared.krillapp.trigger.cron.*
import krill.zone.shared.krillapp.trigger.webhook.*
import org.koin.core.qualifier.*
import org.koin.dsl.*

val clientProcessModule = module {

    factory<CronLogic> { cronLogic }

    single<ClientProcessor> {
        ClientClientProcessor(get(), get(), get(), get(), get(named(IO_SCOPE)))
    }

    single<ServerProcessor> {
        ClientServerProcessor(get(), get(), get(), get())
    }

    single<SpacerProcessor> {
        NoopSpacerProcessor()
    }

    // One shared instance handles all remaining processor interfaces
    single { UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE))) } binds arrayOf(
        PeerProcessor::class,
        ProjectProcessor::class,
        MqttProcessor::class,
        LogicGateProcessor::class,
        CronProcessor::class,
        ButtonProcessor::class,
        DataPointProcessorInterface::class,
        CalculationProcessor::class,
        ComputeProcessor::class,
        LambdaProcessorInterface::class,
        PinProcessor::class,
        TriggerProcessor::class,
        ExecutorProcessorInterface::class,
        WebHookInboundProcessorInterface::class,
        WebHookOutboundProcessorInterface::class,
        SerialDeviceProcessor::class,
        FilterProcessorInterface::class,
        GraphProcessorInterface::class,
        DiagramProcessor::class,
        TaskListProcessor::class,
        JournalProcessor::class,
        LLMProcessor::class,
    )

}