package krill.zone.shared.events

import co.touchlab.kermit.*
import kotlinx.coroutines.flow.*
import org.koin.ext.*

object EventFlowContainer {
    private val logger = Logger.withTag(this::class.getFullName())
    private val _events = MutableSharedFlow<Event>(
        replay = 1,
        extraBufferCapacity = 64
    )
    val events = _events.asSharedFlow()

    suspend fun postEvent(event: Event) {
        logger.i("postEvent: $event")
        if (! EventTracker.dbounce(event)) {
            EventTracker.track(event)
            _events.emit(event)

        } else {
            logger.i("debounced postEvent: $event")
        }

    }

}