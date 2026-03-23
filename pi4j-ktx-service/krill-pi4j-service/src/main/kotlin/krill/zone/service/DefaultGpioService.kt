package krill.zone.service

// pi4j-ktx extension functions are top-level in com.pi4j.ktx
import com.krillforge.pi4j.proto.*
import com.krillforge.pi4j.proto.PullResistance
import com.pi4j.io.gpio.digital.*
import com.pi4j.ktx.io.digital.*
import io.grpc.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.Flow
import krill.zone.*
import org.slf4j.*
import java.util.concurrent.*
import com.pi4j.io.gpio.digital.PullResistance as Pi4jPull

/**
 * gRPC service implementation for GPIO digital I/O.
 *
 * Pin instances are cached by BCM address; the first caller's configuration wins.
 * A [WatchInput] stream stays open until the client cancels — each subscription
 * registers its own [DigitalStateChangeListener] on the same underlying pin object.
 */
class GpioServiceImpl(
    private val ctx: Pi4jContextManager = Pi4jContextManager
) : GpioServiceGrpcKt.GpioServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(GpioServiceImpl::class.java)

    private val inputs = ConcurrentHashMap<Int, DigitalInput>()
    private val outputs = ConcurrentHashMap<Int, DigitalOutput>()

    // ── SetOutput ─────────────────────────────────────────────────────────────

    override suspend fun setOutput(request: SetOutputRequest): PinResponse = runCatching {
        val out = outputs.getOrPut(request.pin) {
            log.debug("Configuring digital output pin {}", request.pin)
            ctx.context.digitalOutput(request.pin) {
                if (request.id.isNotBlank()) id(request.id)
                initial(DigitalState.LOW)
                shutdown(DigitalState.LOW)
            }
        }
        when (request.state) {
            PinState.PIN_STATE_HIGH -> out.high()
            else -> out.low()
        }
        log.debug("Pin {} → {}", request.pin, request.state)
        pinResponse { success = true }
    }.getOrElse { e ->
        log.warn("setOutput pin {}: {}", request.pin, e.message)
        pinResponse { success = false; message = e.message.orEmpty() }
    }

    // ── ToggleOutput ──────────────────────────────────────────────────────────

    override suspend fun toggleOutput(request: PinAddress): PinStateResponse {
        val out = outputs[request.pin]
            ?: throw Status.NOT_FOUND
                .withDescription("Output pin ${request.pin} has not been configured via SetOutput")
                .asException()
        return runCatching {
            out.toggle()
            pinStateResponse { pin = request.pin; state = out.state().toProto() }
        }.getOrElse { e ->
            throw Status.INTERNAL.withDescription(e.message).withCause(e).asException()
        }
    }

    // ── Pulse ─────────────────────────────────────────────────────────────────

    override suspend fun pulse(request: PulseRequest): PinResponse {
        val out = outputs[request.pin]
            ?: throw Status.NOT_FOUND
                .withDescription("Output pin ${request.pin} has not been configured via SetOutput")
                .asException()
        return runCatching {
            val state = if (request.pulseState == PinState.PIN_STATE_HIGH)
                DigitalState.HIGH else DigitalState.LOW
            out.pulse(request.durationMillis.toInt(), TimeUnit.MILLISECONDS, state)
            pinResponse { success = true }
        }.getOrElse { e ->
            pinResponse { success = false; message = e.message.orEmpty() }
        }
    }

    // ── GetOutputState ──────────────────────────────────────────────────────────

    override suspend fun getOutputState(request: PinAddress): PinStateResponse {
        val out = outputs[request.pin]
            ?: throw Status.NOT_FOUND
                .withDescription("Output pin ${request.pin} has not been configured via SetOutput")
                .asException()
        return runCatching {
            pinStateResponse { pin = request.pin; state = out.state().toProto() }
        }.getOrElse { e ->
            throw Status.INTERNAL.withDescription(e.message).withCause(e).asException()
        }
    }

    // ── GetInput ──────────────────────────────────────────────────────────────

    override suspend fun getInput(request: InputConfig): PinStateResponse = runCatching {
        val inp = getOrCreateInput(request)
        pinStateResponse { pin = request.pin; state = inp.state().toProto() }
    }.getOrElse { e ->
        throw Status.INTERNAL.withDescription(e.message).withCause(e).asException()
    }

    // ── WatchInput (server-streaming) ─────────────────────────────────────────
    //
    // Uses callbackFlow to bridge Pi4J's callback-based listener API to a
    // Kotlin Flow. Each gRPC stream subscription registers its own listener
    // on the shared DigitalInput instance so multiple clients can watch the
    // same pin simultaneously.

    override fun watchInput(request: InputConfig): Flow<PinEvent> = callbackFlow {
        val inp = runCatching { getOrCreateInput(request) }.getOrElse { e ->
            close(Status.INTERNAL.withDescription(e.message).asException())
            return@callbackFlow
        }
        log.debug("WatchInput: registering listener on pin {}", request.pin)
        val listener = DigitalStateChangeListener { event ->
            trySend(
                pinEvent {
                    pin = request.pin
                    state = event.state().toProto()
                    timestampNanos = System.nanoTime()
                }
            )
        }
        inp.addListener(listener)
        awaitClose {
            log.debug("WatchInput: removing listener from pin {}", request.pin)
            inp.removeListener(listener)
        }
    }

    // ── UnregisterPin ───────────────────────────────────────────────────────

    override suspend fun unregisterPin(request: PinAddress): PinResponse = runCatching {
        val bcm = request.pin
        var released = false

        outputs.remove(bcm)?.let { out ->
            log.debug("Releasing output pin {}", bcm)
            ctx.context.registry().remove<com.pi4j.io.IO<*, *, *>>(out.id())
            out.close()
            released = true
        }
        inputs.remove(bcm)?.let { inp ->
            log.debug("Releasing input pin {}", bcm)
            ctx.context.registry().remove<com.pi4j.io.IO<*, *, *>>(inp.id())
            inp.close()
            released = true
        }

        if (released) log.info("Unregistered pin {}", bcm)
        pinResponse { success = true; message = if (released) "released" else "not registered" }
    }.getOrElse { e ->
        log.warn("unregisterPin {}: {}", request.pin, e.message)
        pinResponse { success = false; message = e.message.orEmpty() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getOrCreateInput(config: InputConfig): DigitalInput =
        inputs.getOrPut(config.pin) {
            log.debug("Configuring digital input pin {}", config.pin)
            ctx.context.digitalInput(config.pin) {
                if (config.id.isNotBlank()) id(config.id)
                pull(config.pull.toPi4j())
                if (config.debounceMicros > 0) debounce(config.debounceMicros)
            }
        }
}

// ── Extension mappings ────────────────────────────────────────────────────────

private fun DigitalState.toProto(): PinState =
    if (isHigh) PinState.PIN_STATE_HIGH else PinState.PIN_STATE_LOW

private fun PullResistance.toPi4j(): Pi4jPull = when (this) {
    PullResistance.PULL_RESISTANCE_DOWN -> Pi4jPull.PULL_DOWN
    PullResistance.PULL_RESISTANCE_UP -> Pi4jPull.PULL_UP
    else -> Pi4jPull.OFF
}
