package com.krillforge.pi4j

import io.grpc.Channel
import com.krillforge.pi4j.proto.DutyCycleRequest
import com.krillforge.pi4j.proto.FrequencyRequest
import com.krillforge.pi4j.proto.PinAddress
import com.krillforge.pi4j.proto.PwmConfig
import com.krillforge.pi4j.proto.PwmResponse
import com.krillforge.pi4j.proto.PwmServiceGrpcKt
import com.krillforge.pi4j.proto.PwmStatus

class PwmClient internal constructor(channel: Channel) {

    private val stub = PwmServiceGrpcKt.PwmServiceCoroutineStub(channel)

    /** Configure and start a PWM channel. [dutyCycle] is 0.0–100.0. */
    suspend fun configure(pin: Int, frequencyHz: Int, dutyCycle: Float, id: String = ""): PwmResponse =
        stub.configure(PwmConfig.newBuilder().apply {
            this.pin       = pin
            this.frequency = frequencyHz
            this.dutyCycle = dutyCycle
            this.id        = id
        }.build())

    /** Update the duty cycle of a running channel without restarting it. [dutyCycle] is 0.0–100.0. */
    suspend fun setDutyCycle(pin: Int, dutyCycle: Float): PwmResponse =
        stub.setDutyCycle(DutyCycleRequest.newBuilder().apply {
            this.pin       = pin
            this.dutyCycle = dutyCycle
        }.build())

    /** Update the frequency of a running channel without restarting it. */
    suspend fun setFrequency(pin: Int, frequencyHz: Int): PwmResponse =
        stub.setFrequency(FrequencyRequest.newBuilder().apply {
            this.pin       = pin
            this.frequency = frequencyHz
        }.build())

    /** Stop a PWM channel (output goes low). */
    suspend fun stop(pin: Int): PwmResponse =
        stub.stop(PinAddress.newBuilder().setPin(pin).build())

    /** Read back the current configuration of a PWM channel. */
    suspend fun getStatus(pin: Int): PwmStatus =
        stub.getStatus(PinAddress.newBuilder().setPin(pin).build())
}
