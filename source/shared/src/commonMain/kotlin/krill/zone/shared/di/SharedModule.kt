package krill.zone.shared.di

import kotlinx.coroutines.*
import krill.zone.shared.io.*
import krill.zone.shared.io.http.*
import krill.zone.shared.node.*
import org.koin.core.qualifier.*
import org.koin.dsl.*

const val IO_SCOPE = "IO_SCOPE"

val sharedModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<NodeHttp> { NodeHttp(get()) }
    single<TrustHost> { trustHttpClient }

    factory<Multicast> { Multicast(get(named(IO_SCOPE))) }

}