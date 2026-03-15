package krill.zone.shared.di

import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.executor.compute.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.core.module.*
import org.koin.core.qualifier.*
import org.koin.dsl.*


expect val platformModule: Module


val appModule = module {
    single<ClientNodeManager> { ClientNodeManager(get(), get(), get(), get()) }
    single<ServerBoss> { ServerBoss(get()) }


    single<NodeObserver> { DefaultNodeObserver(get()) }


    single<SSEBoss> { SSEBoss(get(), get(named(IO_SCOPE))) }
    single<EventClient> { EventClient(get(), get(named(IO_SCOPE))) }


    factory<ComputeLogic> { DefaultComputeLogic() }

    factory<NodeChildren> { NodeChildren(get()) }
}

