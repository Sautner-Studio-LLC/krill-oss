package krill.zone.shared.events

import kotlinx.coroutines.sync.*

object EventTracker {

    private val processedEvents = mutableMapOf<String, Event >()
    private val mutex = Mutex()

    suspend fun track(event: Event) {
           processedEvents[event.id] = event

    }

    suspend fun isProcessed(id: String): Boolean {
        return processedEvents.contains(id)
    }

    fun dbounce(event: Event) : Boolean {

        processedEvents[event.id]?.let { e ->
            return event.type == e.type && (event.timestamp - e.timestamp) < 1000L
        }
        return false
    }

}