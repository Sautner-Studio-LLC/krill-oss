package krill.zone.app.di

import krill.zone.app.*
import krill.zone.app.io.beacon.*
import krill.zone.shared.di.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.io.http.*
import org.koin.core.qualifier.*
import org.koin.dsl.*

val composeModule = module {

    single<ScreenCore> { DefaultScreenCore(get() ) }

    single<BeaconSender> { ClientBeaconSender(get(), get()) }
    single<BeaconWireHandler> { ClientBeaconWireHandler(get(), get(),  get(named(IO_SCOPE))) }
    single<BeaconSupervisor> { ClientBeaconSupervisor(get(), get(), get(), get(named(IO_SCOPE))) }
    single<ServerConnector> { ClientServerConnector(get(), get(),get(), get(), get(), get(named(IO_SCOPE))) }
}