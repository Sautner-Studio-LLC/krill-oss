package com.krillforge.pi4j.servo

import com.krillforge.pi4j.pca9685.Pca9685Client
import com.krillforge.pi4j.pca9685.Pca9685Registers.CHANNEL_COUNT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * [ServoBank] over one [Pca9685Client]. Every [PulseServo]'s pulse write lands in a staging
 * [IntArray] rather than the wire; [setAngles] flushes it with a single [Pca9685Client.writeFrame]
 * block write, so every touched channel and every previously-set channel land on the same I2C
 * transaction — there is no per-channel write path that could tear.
 */
class Pca9685ServoBank(
    private val pca: Pca9685Client,
    profiles: Map<Int, ServoProfile>,
    private val frequencyHz: Double,
    private val scope: CoroutineScope,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : ServoBank {

    private val ticks = IntArray(CHANNEL_COUNT)
    private val servos: Map<Int, PulseServo> = profiles.mapValues { (channel, profile) ->
        require(channel in 0 until CHANNEL_COUNT) { "channel $channel out of range 0..${CHANNEL_COUNT - 1}" }
        PulseServo(
            initialProfile = profile,
            writePulseUs = { us -> ticks[channel] = ServoMath.pulseUsToTicks(us, frequencyHz) },
            clockMs = clockMs,
        )
    }

    @Volatile
    private var lastUpdateMs: Long = clockMs()
    private var watchdogJob: Job? = null

    override suspend fun setAngles(angles: Map<Int, Double>): Map<Int, ServoCommandResult> {
        val results = angles.mapValues { (channel, deg) ->
            val servo = servos[channel] ?: error("no servo configured for channel $channel")
            servo.angle(deg)
        }
        pca.writeFrame(ticks.copyOf())
        lastUpdateMs = clockMs()
        return results
    }

    override suspend fun torqueOffAll() {
        for (servo in servos.values) servo.torqueOff()
        pca.writeFrame(ticks.copyOf())
        lastUpdateMs = clockMs()
    }

    override suspend fun startWatchdog(watchdogMs: Long) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(watchdogMs)
                if (clockMs() - lastUpdateMs >= watchdogMs) {
                    torqueOffAll()
                }
            }
        }
    }
}
