package krill.zone.shared.events

import co.touchlab.kermit.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.llm.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class EventClient(private val nodeManager: ClientNodeManager, private val bearerTokenProvider: () -> String?, private val scope: CoroutineScope) {

    private val logger = Logger.withTag(this::class.getFullName())
    private val jobs = mutableMapOf<String, Job>()

    /** Server IDs with active SSE connections that have received at least one event. */
    private val _connectedServers = MutableStateFlow<Set<String>>(emptySet())
    val connectedServers: StateFlow<Set<String>> = _connectedServers.asStateFlow()

    fun isConnected(id: String): Boolean {
        return jobs.containsKey(id)
    }

    fun connect(node: Node) {
        if (jobs.containsKey(node.id)) return

        val meta = node.meta as ServerMetaData
        val name = if (SystemInfo.wasmPort > 0) SystemInfo.wasmHost else meta.resolvedHost()
        val sseUrl = URLBuilder(
            protocol = URLProtocol.HTTPS,
            host = name,
            port = meta.port,
            pathSegments = listOf("events")
        ).build()
        val job = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    logger.i { "${node.details()}: establishing SSE connection (attempt $attempt)" }
                    sseHttpClient.sse(
                        urlString = sseUrl.toString(),
                        request = {
                            bearerTokenProvider()?.let { token ->
                                header(HttpHeaders.Authorization, "Bearer $token")
                            }
                        },
                        deserialize = { _, json ->
                            fastJson.decodeFromString<Event>(json)
                        }
                    ) {
                        attempt = 0
                        _connectedServers.update { it + node.id }
                        incoming.collect { incoming ->
                            deserialize<Event>(incoming.data)?.let { event ->
                                logger.i { "Event Received: $event" }

                                // CREATED doesn't require the node to already exist on the client
                                if (event.type == EventType.CREATED) {
                                    nodeManager.update((event.payload as NodeCreatedPayload).node)
                                    return@let
                                }

                                nodeManager.readNodeStateOrNull(event.id).value?.let { currentNode ->
                                    when (event.type) {
                                        EventType.STATE_CHANGE -> {
                                            nodeManager.update(
                                                currentNode.copy(
                                                    state = (event.payload as StateChangeEventPayload).state,
                                                    timestamp = Clock.System.now().toEpochMilliseconds()
                                                )
                                            )
                                        }

                                        EventType.SNAPSHOT_UPDATE -> {
                                            nodeManager.updateSnapshot(
                                                currentNode,
                                                (event.payload as SnapshotUpdatedEventPayload).snapshot
                                            )
                                        }

                                        EventType.PIN_CHANGED -> {
                                            val pinMeta = currentNode.meta as PinMetaData
                                            val payload = event.payload as PinEventPayload
                                            nodeManager.updateMetaData(
                                                currentNode,
                                                pinMeta.copy(state = payload.state)
                                            )
                                        }

                                        EventType.DELETED -> {}
                                        EventType.ACK -> {}
                                        EventType.CREATED -> {} // handled above
                                        EventType.LLM -> {
                                            val payload = event.payload as LLMEventPayload
                                            nodeManager.readNodeStateOrNull(currentNode.id).value?.let { currentNode ->
                                                val meta = currentNode.meta as LLMMetaData
                                                nodeManager.update(currentNode.copy(state = NodeState.NONE, meta = meta.copy(chat = meta.chat.plus(payload.message))))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Clean disconnect — short fixed delay before reconnecting
                    if (isActive) {
                        logger.w { "${node.details()}: SSE closed cleanly, reconnecting in 2s" }
                        delay(2.seconds)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "${node.details()}: SSE failed (attempt $attempt)" }
                    _connectedServers.update { it - node.id }
                    nodeManager.alarm(node)
                    if (isActive) {
                        val backoff = minOf(2000L shl minOf(attempt, 5), 60_000L).milliseconds
                        logger.w { "${node.details()}: reconnecting in $backoff" }
                        delay(backoff)
                        attempt++
                    }
                }
            }
        }

        job.invokeOnCompletion {
            logger.i { "${node.details()}: SSE job completed" }
            _connectedServers.update { it - node.id }
            jobs.remove(node.id)
        }
        jobs[node.id] = job
    }
}