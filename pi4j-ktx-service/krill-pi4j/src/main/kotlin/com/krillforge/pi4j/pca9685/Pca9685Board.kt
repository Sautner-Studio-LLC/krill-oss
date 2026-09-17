package com.krillforge.pi4j.pca9685

import com.krillforge.pi4j.GpioClient
import com.krillforge.pi4j.Pi4jClient
import com.krillforge.pi4j.proto.PinState
import com.krillforge.pi4j.proto.PullResistance

/**
 * A PCA9685 PWM chip plus its OE (output-enable) pin, modeled as one object because you can
 * never safely reason about one without the other — OE is a GPIO, not a PWM channel, but it
 * gates every channel [pwm] controls.
 *
 * ## OE polarity — active-LOW enable
 * ```
 * OE LOW  -> outputs driven from the LEDn registers   (ARMED)
 * OE HIGH -> outputs go to the MODE2.OUTNE state       (DISARMED, LOW given Pca9685Client.init)
 * ```
 * The fault action is OE HIGH. Any doc or API that says "assert OE low to disable" is inverted
 * and would arm the outputs on a fault. [isArmed] is read-only on purpose — when an external MCU
 * owns the pin, this only observes it and never drives it from two places.
 *
 * If OE isn't wired to a GPIO this daemon controls (e.g. tied permanently to ground on a
 * single-board setup), construct with [readOeLow] left `null` — [isArmed] then always reports
 * armed, matching the hardware.
 */
class Pca9685Board(
    val pwm: Pca9685Client,
    private val readOeLow: (suspend () -> Boolean)? = null,
) {

    /** True when outputs are armed (OE reads LOW). Always true if OE has no monitored GPIO. */
    suspend fun isArmed(): Boolean = readOeLow?.invoke() ?: true

    companion object {
        /** Build a [Pca9685Board] whose OE pin is read back through [gpio]. */
        fun withGpio(
            pwm: Pca9685Client,
            gpio: GpioClient,
            oePin: Int,
            pull: PullResistance = PullResistance.PULL_RESISTANCE_OFF,
        ): Pca9685Board = Pca9685Board(pwm) { isOeLow(gpio.getInput(oePin, pull).state) }
    }
}

/**
 * The OE-polarity mapping in one pure, testable place — pulled out of [Pca9685Board.withGpio] so
 * the LOW-means-armed direction can be asserted without a live GPIO. Getting this backwards
 * arms the outputs on every fault; see the OE polarity note on [Pca9685Board].
 */
internal fun isOeLow(state: PinState): Boolean = state == PinState.PIN_STATE_LOW

/**
 * Build a [Pca9685Board] for the PCA9685 at [address] on [bus], using this client's I2C (and,
 * if [oePin] is given, GPIO) sub-clients. Pass `oePin = null` when OE is tied directly to ground.
 */
fun Pi4jClient.pca9685(
    bus: Int = 1,
    address: Int = 0x40,
    oePin: Int? = null,
    oscillatorHz: Double = Pca9685Registers.OSCILLATOR_HZ_NOMINAL,
): Pca9685Board {
    val client = Pca9685Client.over(this.i2c, bus, address, oscillatorHz)
    return if (oePin != null) Pca9685Board.withGpio(client, this.gpio, oePin) else Pca9685Board(client)
}
