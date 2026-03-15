package krill.zone.server.di


import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.server.*
import krill.zone.server.db.*
import krill.zone.server.events.*
import krill.zone.server.io.beacon.*
import krill.zone.server.krillapp.datapoint.*
import krill.zone.server.krillapp.executor.calculation.*
import krill.zone.server.krillapp.executor.cron.*
import krill.zone.server.krillapp.executor.lambda.*
import krill.zone.server.krillapp.executor.logicgate.*
import krill.zone.server.krillapp.mqtt.*
import krill.zone.server.krillapp.server.serial.*
import krill.zone.server.krillapp.trigger.*
import krill.zone.server.logging.*
import krill.zone.shared.*
import krill.zone.shared.di.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.executor.compute.*
import krill.zone.shared.krillapp.executor.lambda.*
import krill.zone.shared.krillapp.executor.mqtt.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.serialdevice.*
import krill.zone.shared.node.persistence.*
import org.koin.core.qualifier.*
import org.koin.dsl.*


private val scopeLogger = Logger.withTag("IO_SCOPE")

val serverModule = module {

    single<CoroutineScope>(named(IO_SCOPE)) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            scopeLogger.e(throwable) { "Uncaught exception in IO_SCOPE" }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }
    single<DataProcessor> { DataProcessor(get(), get(), get())  }
    single<SilentAlarmMonitor> { SilentAlarmMonitor(get(), get()) }
    // Database initialization
    single<NodeRepository> {
        DatabaseConfig.init()
        ExposedNodeRepository()
    }

    single<CronTask> { CronTask(get(), get()) }
    // Provide NodePersistence for NodeManager
    single<NodePersistence> { get<NodeRepository>() }

    single<SerialDeviceManager> {
        ServerSerialDeviceManager(
            get(),
            get(),
            get()
        )
    }
    single<LambdaExecutor> {
        LambdaPythonExecutor(
            get(),
            get()
        )
    }



    single<ServerLifecycleManager> {
        ServerLifecycleManager(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()


        )
    }

    single<PiManager> {
        ServerPiManager(
            get(),
            get()
        )
    }

    single<SerialDirectoryMonitor> {
        SerialDirectoryMonitor(
            get(),
            get()
        )
    }


    single<BeaconSupervisor> { ServerBeaconSupervisor(get(), get(), get(), get(named(IO_SCOPE))) }
    single<BeaconSender> { ServerBeaconSender(get(), get()) }
    single<BeaconWireHandler> { ServerBeaconWireHandler(get(), get(),get(named(IO_SCOPE))) }
    single<ServerConnector> { ServerServerConnector(get(), get(), get(named(IO_SCOPE))) }
    single<ServerNodeManager> { ServerNodeManager(get(), get(), get(), get(), get(named(IO_SCOPE)) ) }
    single<ServerBoss> { ServerBoss(get(named(IO_SCOPE))) }
    single<SilentAlarmMonitor> { SilentAlarmMonitor(get(), get()) }
    single<LogicGateCompute> { LogicGateCompute(get())  }
    single<EventMonitor> { EventMonitor(get(), get(), get()) }
    // Multicast (platform-specific)

    factory<ComputeLogic> { DefaultComputeLogic() }

    factory<MathEvaluator> { Expressions() }

    single<MqttManager> { ServerMqttManager(get()) }

    factory<SandboxConfig> { SandboxConfig() }

    factory<DataStore>  { ServerDataStore() }

    factory<PlatformLogger> {
        PlatformLogger(
            nodeManager = get(),
            scope = get()
        )
    }

}