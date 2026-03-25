@file:OptIn(ExperimentalUuidApi::class)

package krill.zone.server

import co.touchlab.kermit.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import krill.zone.server.krillapp.datapoint.*
import krill.zone.server.krillapp.server.serial.*
import krill.zone.shared.di.*
import krill.zone.shared.krillapp.server.*
import org.koin.core.qualifier.*
import org.koin.ktor.ext.*
import kotlin.uuid.*


/**
 * Main Ktor application module
 * Configures plugins, routes, and lifecycle events for the Krill server
 */
fun Application.module() {
    Logger.setLogWriters(ServerLogWriter(this))
    // Configure all Ktor plugins
    configurePlugins()
    val scope: CoroutineScope by inject(named(IO_SCOPE))

    val lifecycleManager: ServerLifecycleManager by inject()
    val nodeManager: ServerNodeManager by inject()

    val piManager: PiManager by inject()

    val dataProcessor: DataProcessor by inject()

    val serialDirectoryMonitor: SerialDirectoryMonitor by inject()
    val lanTrustTokenProvider: LanTrustTokenProvider by inject()

    routing {
        configureApiRoutes(nodeManager, dataProcessor, piManager, serialDirectoryMonitor, lanTrustTokenProvider, scope)
    }


    // Register lifecycle event handlers
    monitor.subscribe(ApplicationStarted) {
        lifecycleManager.onStarted()
    }

    monitor.subscribe(ServerReady) {
        lifecycleManager.onReady()
    }

    monitor.subscribe(ApplicationStopping) { application ->
        lifecycleManager.onStopping(application)
    }

    monitor.subscribe(ApplicationStopped) { application ->
        lifecycleManager.onStopped(application)
    }

}

