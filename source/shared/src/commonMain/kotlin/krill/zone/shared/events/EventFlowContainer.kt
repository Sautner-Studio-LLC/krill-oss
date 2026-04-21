package krill.zone.shared.events

import co.touchlab.kermit.*
import kotlinx.coroutines.flow.*
import org.koin.ext.*

object EventFlowContainer {
    private val logger = Logger.withTag(this::class.getFullName())

    /**
     * Buffer sized for high-frequency ingest bursts (PROCESSING pulses).
     * Default SUSPEND overflow policy still applies — persistent events
     * emitted via [postEvent] backpressure on saturation so [SNAPSHOT_UPDATE]
     * and [WARN] cannot be lost. Callers that can tolerate loss (cosmetic
     * pulses) should use [tryPostEvent].
     */
    private val _events = MutableSharedFlow<Event>(
        replay = 1,
        extraBufferCapacity = BUFFER_CAPACITY
    )
    val events = _events.asSharedFlow()

    suspend fun postEvent(event: Event) {
        logger.d("postEvent: $event")
        if (! EventTracker.dbounce(event)) {
            EventTracker.track(event)
            _events.emit(event)

        } else {
            logger.d("debounced postEvent: $event")
        }

    }

    /**
     * Non-blocking emit for loss-tolerant events (e.g. PROCESSING pulses).
     * Returns `true` if the event was accepted into the buffer,
     * `false` if the buffer was full (event dropped).
     *
     * Unlike [postEvent] this does NOT update [EventTracker], so rapid
     * duplicates within the debounce window are not suppressed — acceptable
     * for cosmetic pulses where the client already collapses them via its
     * 1500 ms reset window.
     */
    fun tryPostEvent(event: Event): Boolean {
        if (EventTracker.dbounce(event)) {
            logger.d("debounced tryPostEvent: $event")
            return true
        }
        return _events.tryEmit(event)
    }

    const val BUFFER_CAPACITY = 256
}