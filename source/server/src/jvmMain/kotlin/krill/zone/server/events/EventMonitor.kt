package krill.zone.server.events

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.server.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.persistence.*
import org.koin.ext.*

class EventMonitor(private val nodeManager: ServerNodeManager, private val nodePersistence: NodePersistence,  private val scope : CoroutineScope): ServerTask {

    private val logger = Logger.withTag(this::class.getFullName())

    override suspend fun start() {
        EventFlowContainer.events.collect { event ->


            logger.d(  "Server Event Collected: $event")
            scope.launch {
                when (event.type) {
                    EventType.STATE_CHANGE ->  {}
                    EventType.SNAPSHOT_UPDATE, EventType.PIN_CHANGED -> {
                        nodePersistence.read(event.id)?.let { node ->
                            nodeManager.executeSources(node)
                        }

                    }
                    EventType.DELETED -> {}
                    EventType.ACK -> {}
                }
            }

        }
    }


}