@file:OptIn(ExperimentalUuidApi::class)

package krill.zone.server

import co.touchlab.kermit.*
import io.ktor.server.application.*
import kotlinx.coroutines.*
import krill.zone.server.events.*
import krill.zone.server.krillapp.executor.cron.*
import krill.zone.server.krillapp.project.tasklist.*
import krill.zone.server.krillapp.server.backup.*
import krill.zone.server.krillapp.server.pin.*
import krill.zone.server.krillapp.server.serial.*
import krill.zone.server.krillapp.trigger.*
import krill.zone.server.logging.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.executor.lambda.*
import krill.zone.shared.krillapp.executor.mqtt.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.serialdevice.*
import org.koin.ext.*
import java.io.*
import kotlin.uuid.*


/**
 * Manages server lifecycle events and initialization
 */
internal class ServerLifecycleManager(
    private val nodeManager: ServerNodeManager,
    private val serverBoss: ServerBoss,
    private val platformLogger: PlatformLogger,
    private val dataStore: DataStore,
    private val piManager: PiManager,
    private val serial: SerialDirectoryMonitor,
    private val lambdaExecutor: LambdaExecutor,
    private val serialDeviceManager: SerialDeviceManager,
    private val eventMonitor: EventMonitor,
    private val cronTask: CronTask,
    private val taskListExpiryTask: TaskListExpiryTask,
    private val silentAlarmWatchdogTask: SilentAlarmWatchdogTask,
    private val mqttManager: MqttManager,
    private val pinReconciliationTask: PinReconciliationTask,
    private val pinProvider: PinProvider,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag(this::class.getFullName())

    /**
     * Handles server startup initialization
     */
    fun onStarted() {
        logger.i("Server is started")

    }

    /**
     * Handles server ready state
     */
    fun onReady() {
        logger.i("Server is ready ${SystemInfo.isServer()} ")
            PinProviderContainer.init(pinProvider)
           pinProvider.bearerToken()?.let { token ->
               logger.i("Token is $token")
           }
            try {
                nodeManager.init {
                    val job = scope.launch {
                        val server = nodeManager.readNodeState(installId()).value
                        if (platform == Platform.RASPBERRY_PI) {

                            piManager.init {
                                scope.launch {
                                    piManager.initPins()
                                    PiManagerContainer.init(piManager)
                                    updateServerInfo()
                                }

                            }

                        } else {
                            updateServerInfo()
                        }



                        val meta = server.meta as ServerMetaData

                        SerialManagerContainer.init(serialDeviceManager)
                        LambdaExecutorContainer.init(lambdaExecutor)

                        DataStoreContainer.init(dataStore)
                        MqttContainer.init(mqttManager)
                        if (meta.loggingEnabled) {
                            serverBoss.addTask(platformLogger)
                        }
                        serverBoss.addTask(serial)
                        serverBoss.addTask(eventMonitor)
                        serverBoss.addTask(cronTask)
                        serverBoss.addTask(taskListExpiryTask)
                        serverBoss.addTask(silentAlarmWatchdogTask)
                        if (platform == Platform.RASPBERRY_PI) {
                            serverBoss.addTask(pinReconciliationTask)
                        }
                        serverBoss.addTask(BackupCleanupTask())
                        serverBoss.start()
                    }
                    job.invokeOnCompletion {
                        // Execute the server node to trigger startup
                        nodeManager.readNodeState(installId()).value.let { node ->
                            nodeManager.execute(node)
                        }
                        logger.i("Server Startup is completed")
                        SystemInfo.setReady(true)
                    }
                }
            } catch (ex: Exception) {
                logger.e("Failed to initialize platform-specific features", ex)
            }

    }

    private fun updateServerInfo() {
        if (!nodeManager.nodeAvailable(installId())) return
        val server = nodeManager.readNodeState(installId()).value
        val meta = server.meta as ServerMetaData
        val version = File("/etc/krill/version").readText().trim()
        val serverInfo = if (platform == Platform.RASPBERRY_PI) {
            piManager.getServerInfo()
        } else {
            SystemCommandOperator.getSystemInfo()
        }
        // Detect camera presence
        val cameraAvailable = try {
            val process = ProcessBuilder("rpicam-still", "--list-cameras")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("Available cameras") && output.contains(" : ")
        } catch (e: Exception) {
            false
        }

        nodeManager.updateMetaData(
            server, meta.copy(
                version = version,
                os = serverInfo.os.trim(),
                model = serverInfo.model,
                platform = platform,
                cameraAvailable = cameraAvailable,
            )
        )
    }

    /**
     * Handles server stopping
     */
    fun onStopping(application: Application) {
        application.environment.log.info("Server is stopping")
        scope.cancel("Server stopped")
    }

    /**
     * Handles server stopped
     */
    fun onStopped(application: Application) {
        application.environment.log.info("Server is stopped")
        // Resources are cleaned up by scope cancellation
    }
}
