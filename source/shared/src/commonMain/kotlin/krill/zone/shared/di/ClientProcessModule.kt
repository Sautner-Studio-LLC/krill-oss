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

        ClientServerProcessor(get(), get(), get(),get())

    }

    single<PeerProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<ProjectProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<MqttProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<LogicGateProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<CronProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<ButtonProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<SpacerProcessor> {
        NoopSpacerProcessor()
    }

    single<DataPointProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<CalculationProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<ComputeProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<LambdaProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<PinProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<TriggerProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<ExecutorProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }


    single<WebHookInboundProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<WebHookOutboundProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<SerialDeviceProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<FilterProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<GraphProcessorInterface> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<DiagramProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<TaskListProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

    single<JournalProcessor> {

        UniversalAppNodeProcessor(get(), get(), get(), get(named(IO_SCOPE)))

    }

}

