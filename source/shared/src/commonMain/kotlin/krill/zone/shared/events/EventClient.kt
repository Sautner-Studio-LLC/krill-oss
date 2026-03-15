package krill.zone.shared.events

import co.touchlab.kermit.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*
import kotlin.time.*

class EventClient(private val nodeManager: ClientNodeManager, private val scope: CoroutineScope) {

    private val logger = Logger.withTag(this::class.getFullName())
    private val mutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    fun isConnected(id: String): Boolean {
        return jobs.containsKey(id)
    }

    fun connect(node: Node) {

        if (jobs.contains(node.id)) {
            return
        }
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.e(exception) { "${node.details()}: EventClient uncaught exception" }
            nodeManager.alarm(node)
        }
        scope.launch(handler) {

            try {
                mutex.withLock {
                    val meta = node.meta as ServerMetaData
                    val name = if (SystemInfo.wasmApiKey?.isNotEmpty() == true) {
                        "localhost"
                    } else meta.name
                    logger.i { "${node.details()} establishing event client" }
                    val sseUrl = URLBuilder(
                        protocol = URLProtocol.HTTPS,
                        host = name,
                        port = meta.port,
                        pathSegments = listOf("events")
                    ).build()
                    val apiKey = meta.apiKey
                    val job = scope.launch(handler) {
                        try {
                            httpClient.sse(
                                urlString = sseUrl.toString(),
                                request = {
                                    header(HttpHeaders.Authorization, "Bearer $apiKey")
                                },
                                deserialize = { _, json ->
                                    fastJson.decodeFromString<Event>(json)
                                }

                            ) {
                                while (currentCoroutineContext().isActive) {
                                    incoming.collect { incoming ->
                                        deserialize<Event>(incoming.data)?.let { event ->
                                            logger.i { "Event Recieved: $event" }
                                            nodeManager.readNodeStateOrNull(event.id).value?.let { node ->
                                                when (event.type) {
                                                    EventType.STATE_CHANGE -> {
                                                        nodeManager.update(
                                                            node.copy(
                                                                state = (event.payload as StateChangeEventPayload).state,
                                                                timestamp = Clock.System.now().toEpochMilliseconds()
                                                            )
                                                        )
                                                    }

                                                    EventType.SNAPSHOT_UPDATE -> {
                                                        nodeManager.updateSnapshot(
                                                            node,
                                                            (event.payload as SnapshotUpdatedEventPayload).snapshot
                                                        )
                                                    }

                                                    EventType.PIN_CHANGED -> {
                                                        val meta = node.meta as PinMetaData
                                                        val payload = event.payload as PinEventPayload
                                                        nodeManager.updateMetaData(
                                                            node,
                                                            meta.copy(state = payload.state)
                                                        )
                                                    }

                                                    EventType.DELETED -> {}
                                                    EventType.ACK -> {}
                                                }

                                            }

                                        }
                                    }
                                }

                            }
                        } catch (e: Exception) {
                            logger.e(e) { "${node.details()}: EventClient SSE connecting failed" }
                            nodeManager.alarm(node)
                        } finally {
                            logger.w { "${node.details()}: EventClient SSEFinally" }
                            jobs[node.id]?.cancel()
                            jobs.remove(node.id)
                        }
                    }

                    job.invokeOnCompletion {
                        logger.i { "${node.details()}: completing sse job" }
                        jobs.remove(node.id)
                    }
                    jobs[node.id] = job


                }
            } catch (e: Exception) {
                logger.e(e) { "${node.details()}: EventClient outer launch failed" }
                nodeManager.alarm(node)
            }
        }


    }
}