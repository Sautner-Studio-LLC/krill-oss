package krill.zone.shared.di

import krill.zone.shared.node.manager.*
import org.koin.dsl.*


val clientNodeManagerModule = module {
    single<ClientNodeManager> { ClientNodeManager(get(), get(),  get() , get()) }

}
