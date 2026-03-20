package com.krillforge.pi4j

import io.grpc.Channel
import kotlinx.coroutines.flow.Flow
import com.krillforge.pi4j.proto.GpioServiceGrpcKt
import com.krillforge.pi4j.proto.InputConfig
import com.krillforge.pi4j.proto.PinAddress
import com.krillforge.pi4j.proto.PinEvent
import com.krillforge.pi4j.proto.PinResponse
import com.krillforge.pi4j.proto.PinState
import com.krillforge.pi4j.proto.PinStateResponse
import com.krillforge.pi4j.proto.PullResistance
import com.krillforge.pi4j.proto.PulseRequest
import com.krillforge.pi4j.proto.SetOutputRequest

class GpioClient internal constructor(channel: Channel) {

    private val stub = GpioServiceGrpcKt.GpioServiceCoroutineStub(channel)

    /** Configure an output pin and drive it high or low immediately. */
    suspend fun setOutput(pin: Int, high: Boolean, id: String = ""): PinResponse =
        stub.setOutput(SetOutputRequest.newBuilder().apply {
            this.pin   = pin
            this.state = if (high) PinState.PIN_STATE_HIGH else PinState.PIN_STATE_LOW
            this.id    = id
        }.build())

    /** Toggle a previously configured output pin and return the new state. */
    suspend fun toggleOutput(pin: Int): PinStateResponse =
        stub.toggleOutput(PinAddress.newBuilder().setPin(pin).build())

    /**
     * Pulse a pin to [pulseHigh] state for [durationMillis] milliseconds,
     * then revert to the opposite state.
     */
    suspend fun pulse(pin: Int, durationMillis: Long, pulseHigh: Boolean = true): PinResponse =
        stub.pulse(PulseRequest.newBuilder().apply {
            this.pin            = pin
            this.durationMillis = durationMillis
            this.pulseState     = if (pulseHigh) PinState.PIN_STATE_HIGH else PinState.PIN_STATE_LOW
        }.build())

    /** Read the current digital state of an input pin. */
    suspend fun getInput(
        pin: Int,
        pull: PullResistance = PullResistance.PULL_RESISTANCE_OFF,
        debouncesMicros: Long = 0L,
        id: String = "",
    ): PinStateResponse = stub.getInput(inputConfig(pin, pull, debouncesMicros, id))

    /**
     * Returns a [Flow] that emits a [PinEvent] on every state change of the input pin.
     * The flow stays active until cancelled.
     *
     * ```kotlin
     * client.gpio.watchInput(pin = 27).collect { event ->
     *     println("pin ${event.pin} → ${event.state} at ${event.timestampNanos}")
     * }
     * ```
     */
    fun watchInput(
        pin: Int,
        pull: PullResistance = PullResistance.PULL_RESISTANCE_OFF,
        debouncesMicros: Long = 0L,
        id: String = "",
    ): Flow<PinEvent> = stub.watchInput(inputConfig(pin, pull, debouncesMicros, id))

    private fun inputConfig(
        pin: Int,
        pull: PullResistance,
        debouncesMicros: Long,
        id: String,
    ) = InputConfig.newBuilder().apply {
        this.pin            = pin
        this.pull           = pull
        this.debounceMicros = debouncesMicros
        this.id             = id
    }.build()
}
