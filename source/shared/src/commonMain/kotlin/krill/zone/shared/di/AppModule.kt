package krill.zone.shared.di

import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.executor.compute.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import krill.zone.shared.security.*
import org.koin.core.module.*
import org.koin.core.qualifier.*
import org.koin.dsl.*


expect val platformModule: Module


val appModule = module {
    single<ServerBoss> { ServerBoss(get()) }
    single<NodeObserver> { DefaultNodeObserver(get()) }
    single<EventClient> {
        val pinStore: ClientPinStore? = getOrNull()
        EventClient(get(), bearerTokenProvider = { pinStore?.bearerToken() }, get(named(IO_SCOPE)))
    }
    factory<ComputeLogic> { DefaultComputeLogic() }
    factory<NodeChildren> { NodeChildren(get()) }
}

