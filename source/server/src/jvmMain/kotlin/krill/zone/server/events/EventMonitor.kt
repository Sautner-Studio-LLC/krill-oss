package krill.zone.server.events

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.persistence.*
import org.koin.ext.*
import kotlin.time.*

class EventMonitor(private val nodeManager: ServerNodeManager, private val nodePersistence: NodePersistence,  private val scope : CoroutineScope): ServerTask {

    private val logger = Logger.withTag(this::class.getFullName())

    override suspend fun start() {
        EventFlowContainer.events.collect { event ->


            logger.d(  "Server Event Collected: $event")
            scope.launch {
                when (event.type) {
                    EventType.STATE_CHANGE ->  {}
                    EventType.SNAPSHOT_UPDATE -> {
                        nodePersistence.read(event.id)?.let { node ->
                            nodeManager.executeSources(node)
                        }
                    }
                    EventType.PIN_CHANGED -> {
                        nodePersistence.read(event.id)?.let { node ->
                            // Persist the pin state from the event payload before executing
                            // downstream sources. Without this, the logic gate reads live
                            // hardware which may have bounced by the time it processes.
                            if (node.type == KrillApp.Server.Pin && event.payload is PinEventPayload) {
                                val meta = node.meta as PinMetaData
                                val payload = event.payload as PinEventPayload
                                val updatedNode = node.copy(
                                    meta = meta.copy(state = payload.state),
                                    timestamp = Clock.System.now().toEpochMilliseconds()
                                )
                                nodePersistence.save(updatedNode)
                                nodeManager.executeSources(updatedNode)
                            } else {
                                nodeManager.executeSources(node)
                            }
                        }
                    }
                    EventType.DELETED -> {}
                    EventType.ACK -> {}
                    EventType.LLM ->{}
                    EventType.CREATED -> {
                        (event.payload as NodeCreatedPayload).node?.let { node ->
                            nodePersistence.save(node)
                        }
                    }
                }
            }

        }
    }


}