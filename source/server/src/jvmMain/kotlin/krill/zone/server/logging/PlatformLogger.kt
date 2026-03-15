package krill.zone.server.logging

import androidx.compose.runtime.*
import co.touchlab.kermit.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.logging.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.krillapp.server.*
import org.koin.core.component.*
import org.koin.ext.*
import kotlin.time.*

object ErrorContainer : KoinComponent {

    private val scope: CoroutineScope by inject()

    private val store = mutableListOf<Error>()
    private val mutex = Mutex()
    fun add(error: Error) {
        scope.launch {
            mutex.withLock {
                store.add(error)
            }
        }


    }

    suspend fun size(): Int {
        mutex.withLock {
            return store.size
        }
    }

    suspend fun drain(): List<Error> {
        mutex.withLock {
            val copy = store.toList()
            store.clear()
            return copy
        }
    }
}

class PlatformLogger(
    private val nodeManager: ServerNodeManager,

    private val scope: CoroutineScope
) : ServerTask {
    private val logger = Logger.withTag(this::class.getFullName())
    private val job = mutableStateOf<Job?>(null)
    private val mutex = Mutex()

    private val wait = 1000 * 60 * 60

    private var lastNodeCount = 0


    @OptIn(ExperimentalTime::class)
    override suspend fun start() {

        logger.i("started observing system info for logging")
        mutex.withLock {
            if (job.value == null) {
                job.value = scope.launch {
                    while (currentCoroutineContext().isActive) {
                        delay(wait.toLong())
                        if (!nodeManager.nodeAvailable(installId())) continue
                        val host = nodeManager.readNodeState(installId()).value
                        val meta = host.meta as ServerMetaData
                        if (meta.loggingEnabled) {
                            logger.i("processing logs")
                            if (nodeManager.nodes().size != lastNodeCount || ErrorContainer.size() > 0) {
                                lastNodeCount = nodeManager.nodes().size
                                postLogs()
                            }
                        } else {
                            //dump logs if logging disabled.
                            ErrorContainer.drain()
                        }
                    }
                }
            }
        }
    }


    private suspend fun postLogs() {
        val nodes = mutableListOf<String>()
        val errors = ErrorContainer.drain()
        nodeManager.nodes().forEach { node ->
            nodes.add(node.type.toString())
        }
        val log = KrillLog(
            timestamp = System.currentTimeMillis(),
            appVersion = readVersion(),
            errors = errors,
            installId = installId(),
            nodes = nodes
        )
        logger.w(fastJson.encodeToString(log))

        val http = buildCioClient()
        try {
            val response = http.post(API_GW) {
                contentType(ContentType.Application.Json)
                setBody(log)
            }
            if (response.status.isSuccess()) {
                logger.i("${response.status.value} $log")
            } else {
                logger.e("Failed to post ${response.status.value} logs: ${response.status.description}")
            }
        } catch (e: Exception) {
            logger.e(e) { "Error posting logs: ${e.localizedMessage}" }
        } finally {
            http.close()
        }
    }


    private fun readVersion(): String {
        return try {
            val versionFile = java.io.File("/etc/krill/version")
            if (versionFile.exists()) {
                versionFile.readText().removeSuffix("\n").trim()
            } else {
                "0.0.0"
            }
        } catch (e: Exception) {
            logger.e("Failed to read version file", e)
            "0.0.0"
        }
    }

    private fun buildCioClient(): HttpClient {
        return HttpClient(CIO) {


            install(ContentNegotiation) {
                json(fastJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                // No socketTimeoutMillis - WebSocket connections should stay open indefinitely
            }
        }
    }

    companion object {
        private const val API_GW = "https://oazxfaz177.execute-api.us-east-1.amazonaws.com/prod/logs"
    }

}