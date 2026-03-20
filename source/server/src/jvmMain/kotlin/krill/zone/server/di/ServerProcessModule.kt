package krill.zone.server.di

import krill.zone.server.*
import krill.zone.server.krillapp.client.*
import krill.zone.server.krillapp.datapoint.*
import krill.zone.server.krillapp.datapoint.graph.*
import krill.zone.server.krillapp.executor.calculation.*
import krill.zone.server.krillapp.executor.compute.*
import krill.zone.server.krillapp.executor.lambda.*
import krill.zone.server.krillapp.executor.logicgate.*
import krill.zone.server.krillapp.executor.mqtt.*
import krill.zone.server.krillapp.executor.webhook.*
import krill.zone.server.krillapp.project.*
import krill.zone.server.krillapp.server.llm.*
import krill.zone.server.krillapp.server.pin.*
import krill.zone.server.krillapp.server.serial.*
import krill.zone.server.krillapp.trigger.*
import krill.zone.server.krillapp.trigger.button.*
import krill.zone.server.krillapp.trigger.cron.*
import krill.zone.server.krillapp.trigger.webhook.*
import krill.zone.shared.*
import krill.zone.shared.di.*
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

val serverProcessModule = module {

    factory<CronLogic> { cronLogic }

    single<ClientProcessor> {

        ServerClientProcessor()

    }

    single<ServerProcessor> {

        ServerServerProcessor(get(), get(), get(named(IO_SCOPE)))

    }

    single<LLMProcessor> {

        ServerLLMProcessor(get(),get(),get(named(IO_SCOPE)))

    }

    single<PeerProcessor> {

        ServerPeerProcessor()

    }

    single<ProjectProcessor> {

        ServerProjectProcessor()

    }

    single<MqttProcessor> {

        ServerMqttProcessor(get(), get(), get(named(IO_SCOPE)))

    }

    single<LogicGateProcessor> {

        ServerLogicGateProcessor(get(), get(), get(), get())

    }

    single<CronProcessor> {

        ServerCronProcessor(get(), get(named(IO_SCOPE)))

    }

    single<ButtonProcessor> {

        ServerButtonProcessor(get(), get(named(IO_SCOPE)))

    }

    single<SpacerProcessor> {
        NoopSpacerProcessor( )
    }

    single<DataPointProcessorInterface> {

        ServerDataPointProcessor(get(), get(), get(named(IO_SCOPE)))

    }

    single<CalculationProcessor> {

        ServerCalculationProcessor(get(), get(), get(), get())

    }

    single<ComputeProcessor> {

        ServerComputeProcessor(get(), get(), get())

    }

    single<LambdaProcessorInterface> {

        ServerLambdaProcessor(get(), get(), get(named(IO_SCOPE)))

    }

    single<PinProcessor> {

        ServerPinProcessor(get(), get(named(IO_SCOPE)))

    }

    single<TriggerProcessor> {

        ServerTriggerProcessor(get(), get(), get(named(IO_SCOPE)))

    }

    single<ExecutorProcessorInterface> {

        ServerExecutorProcessor(get(named(IO_SCOPE)))

    }


    single<WebHookInboundProcessorInterface> {

        ServerWebHookInboundProcessor(get(), get(named(IO_SCOPE)))

    }

    single<WebHookOutboundProcessorInterface> {

        ServerWebHookOutboundProcessor(get(), get(), get(named(IO_SCOPE)))

    }

    single<SerialDeviceProcessor> {

        ServerSerialDeviceProcessor(get(), get(named(IO_SCOPE)))

    }

    single<FilterProcessorInterface> {

        ServerFilterProcessor(get(named(IO_SCOPE)))

    }

    single<GraphProcessorInterface> {

        ServerGraphProcessor(get(), get())

    }

    single<DiagramProcessor> {

        ServerDiagramProcessor( )

    }

    single<TaskListProcessor> {

        ServerTaskListProcessor()

    }

    single<JournalProcessor> {

        ServerJournalProcessor()

    }

}

