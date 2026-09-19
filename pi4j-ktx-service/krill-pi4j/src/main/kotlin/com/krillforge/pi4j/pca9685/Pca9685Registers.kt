package com.krillforge.pi4j.pca9685

import kotlin.math.roundToInt

/**
 * PCA9685 register map and derived constants (16-channel, 12-bit PWM driver over I2C).
 *
 * See the NXP PCA9685 datasheet §7 for the authoritative bit layout. Values here are the ones
 * this driver relies on; not a full transcription of the register set.
 */
object Pca9685Registers {

    const val MODE1: Int = 0x00
    const val MODE2: Int = 0x01
    const val ALLCALLADR: Int = 0x05
    const val LED0_ON_L: Int = 0x06
    const val ALL_LED_OFF_H: Int = 0xFD
    const val PRE_SCALE: Int = 0xFE

    // MODE1 bits
    const val MODE1_RESTART: Int = 0x80
    const val MODE1_AI: Int = 0x20
    const val MODE1_SLEEP: Int = 0x10
    const val MODE1_ALLCALL: Int = 0x01

    // MODE2 bits — OUTDRV totem-pole, OUTNE=00 so a disabled/asleep channel is driven LOW
    // rather than high-impedance (see Pca9685Board KDoc on OE polarity).
    const val MODE2_OUTDRV: Int = 0x04

    /** LEDn_ON_H / LEDn_OFF_H bit 4 — full-on / full-off override, independent of the 12-bit count. */
    const val LED_FULL_BIT: Int = 0x10

    /** Default 7-bit ALLCALL broadcast address every PCA9685 answers to unless disabled. */
    const val ALL_CALL_ADDRESS_DEFAULT: Int = 0x70

    const val CHANNEL_COUNT: Int = 16
    const val TICKS_PER_CYCLE: Int = 4096

    /** Nominal internal RC oscillator frequency. Real parts measure ~23-27 MHz — calibrate per board. */
    const val OSCILLATOR_HZ_NOMINAL: Double = 25_000_000.0

    /** Register holding the low byte of channel [channel]'s ON count (LEDn base = 0x06 + 4n). */
    fun ledOnLowRegister(channel: Int): Int {
        require(channel in 0 until CHANNEL_COUNT) { "channel $channel out of range 0..${CHANNEL_COUNT - 1}" }
        return LED0_ON_L + 4 * channel
    }

    /**
     * PRE_SCALE for a target PWM frequency, clamped to the chip's documented range
     * (3..255, i.e. ~1526 Hz down to ~24 Hz at the nominal oscillator).
     */
    fun prescaleFor(targetHz: Double, oscillatorHz: Double = OSCILLATOR_HZ_NOMINAL): Int {
        require(targetHz > 0) { "targetHz must be positive, was $targetHz" }
        val raw = (oscillatorHz / (TICKS_PER_CYCLE * targetHz)).roundToInt() - 1
        return raw.coerceIn(3, 255)
    }
}
