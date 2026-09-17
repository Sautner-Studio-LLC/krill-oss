package com.krillforge.pi4j.pca9685

import com.krillforge.pi4j.pca9685.Pca9685Registers.ALL_LED_OFF_H
import com.krillforge.pi4j.pca9685.Pca9685Registers.CHANNEL_COUNT
import com.krillforge.pi4j.pca9685.Pca9685Registers.LED0_ON_L
import com.krillforge.pi4j.pca9685.Pca9685Registers.LED_FULL_BIT
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE1
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE1_ALLCALL
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE1_AI
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE1_RESTART
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE1_SLEEP
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE2
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE2_OUTDRV
import com.krillforge.pi4j.pca9685.Pca9685Registers.OSCILLATOR_HZ_NOMINAL
import com.krillforge.pi4j.pca9685.Pca9685Registers.PRE_SCALE
import com.krillforge.pi4j.pca9685.Pca9685Registers.TICKS_PER_CYCLE
import com.krillforge.pi4j.pca9685.Pca9685Registers.ledOnLowRegister
import com.krillforge.pi4j.pca9685.Pca9685Registers.prescaleFor
import kotlinx.coroutines.delay

/**
 * PCA9685 chip-level driver: 16 channels of 12-bit PWM over one I2C device address.
 *
 * Deliberately built against [I2cBus] rather than the gRPC [com.krillforge.pi4j.I2cClient]
 * directly, so the sleep/prescale dance and the frame-write byte layout can be asserted exactly
 * in tests with a fake bus — no daemon, no gRPC, no hardware. Use [over] to wire up the real
 * daemon-backed client.
 *
 * OE (output-enable) is not part of this class — see [Pca9685Board], which composes this with
 * the GPIO pin that drives it. A chip and its enable pin should never be reasoned about apart.
 */
class Pca9685Client(
    private val bus: I2cBus,
    private val oscillatorHz: Double = OSCILLATOR_HZ_NOMINAL,
) {

    /**
     * MODE1 = AI|ALLCALL, MODE2 = OUTDRV (OUTNE=00, so a disabled channel drives LOW rather than
     * floating), sets [frequencyHz], then forces every channel off (ALL_LED_OFF_H) so nothing
     * glitches on with whatever the registers powered up holding.
     */
    suspend fun init(frequencyHz: Double) {
        bus.writeRegister(MODE1, MODE1_AI or MODE1_ALLCALL)
        bus.writeRegister(MODE2, MODE2_OUTDRV)
        setFrequency(frequencyHz)
        bus.writeRegister(ALL_LED_OFF_H, LED_FULL_BIT)
    }

    /**
     * PRE_SCALE is only writable while SLEEP=1 (datasheet §7.3.5) — writing it any other time is
     * the classic silent-no-op PCA9685 bug: the call succeeds and the frequency never changes.
     *
     * Sequence: read MODE1, sleep (RESTART cleared), write PRE_SCALE, wake, wait for the
     * oscillator to stabilise (datasheet: >=500us), set RESTART, then **read PRE_SCALE back and
     * reject if it doesn't match** rather than trusting the write.
     */
    suspend fun setFrequency(targetHz: Double) {
        val prescale = prescaleFor(targetHz, oscillatorHz)

        val mode1 = bus.readRegister(MODE1)
        val asleep = (mode1 or MODE1_SLEEP) and MODE1_RESTART.inv()
        bus.writeRegister(MODE1, asleep)
        bus.writeRegister(PRE_SCALE, prescale)

        val awake = asleep and MODE1_SLEEP.inv()
        bus.writeRegister(MODE1, awake)
        delay(1) // datasheet: oscillator needs >=500us to stabilise after SLEEP clears
        bus.writeRegister(MODE1, awake or MODE1_RESTART)

        val readBack = bus.readRegister(PRE_SCALE)
        check(readBack == prescale) {
            "PCA9685 PRE_SCALE write did not take: wrote $prescale, read back $readBack " +
                "(PRE_SCALE is only writable while MODE1.SLEEP=1)"
        }
    }

    /**
     * Set one channel's duty width in ticks (0..4096 of a 4096-tick cycle). The channel's ON
     * edge is staggered across the cycle (see [writeFrame]) so driving a single channel doesn't
     * line its edge up with every other channel already running.
     */
    suspend fun setChannel(channel: Int, dutyTicks: Int) {
        val onCount = staggeredOnCount(channel)
        bus.writeBytes(ledOnLowRegister(channel), packChannel(onCount, dutyTicks))
    }

    /**
     * Write all 16 channels in a single 64-byte auto-increment block write starting at
     * LED0_ON_L — the only way to change every channel on the same I2C transaction instead of
     * tearing across up to 16 separate ones.
     *
     * ON counts are staggered (`on_n = n * 4096/16`) rather than all starting at 0, so 16
     * channels don't switch on the same edge and brown out the supply rail. [dutyTicks] holds
     * the width of each channel's pulse in ticks (0 = full off, 4096 = full on).
     */
    suspend fun writeFrame(dutyTicks: IntArray) {
        require(dutyTicks.size == CHANNEL_COUNT) {
            "expected $CHANNEL_COUNT channel counts, got ${dutyTicks.size}"
        }
        val frame = ByteArray(CHANNEL_COUNT * 4)
        for (channel in dutyTicks.indices) {
            packChannel(staggeredOnCount(channel), dutyTicks[channel]).copyInto(frame, destinationOffset = channel * 4)
        }
        bus.writeBytes(LED0_ON_L, frame)
    }

    /**
     * A PCA9685 brownout reset is silent: MODE1 returns to its 0x11 power-on value (SLEEP=1),
     * every output stops, and no error is ever reported. Poll this and treat `true` as a fault.
     */
    suspend fun isAsleep(): Boolean = (bus.readRegister(MODE1) and MODE1_SLEEP) != 0

    private fun staggeredOnCount(channel: Int): Int {
        require(channel in 0 until CHANNEL_COUNT) { "channel $channel out of range 0..${CHANNEL_COUNT - 1}" }
        return channel * TICKS_PER_CYCLE / CHANNEL_COUNT
    }

    private fun packChannel(onCount: Int, dutyTicks: Int): ByteArray {
        require(dutyTicks in 0..TICKS_PER_CYCLE) { "dutyTicks $dutyTicks out of range 0..$TICKS_PER_CYCLE" }
        return when (dutyTicks) {
            0 -> byteArrayOf(0, 0, 0, LED_FULL_BIT.toByte()) // full off wins over any ON value
            TICKS_PER_CYCLE -> byteArrayOf(0, LED_FULL_BIT.toByte(), 0, 0) // full on
            else -> {
                val offCount = (onCount + dutyTicks) % TICKS_PER_CYCLE
                byteArrayOf(
                    (onCount and 0xFF).toByte(),
                    ((onCount shr 8) and 0x0F).toByte(),
                    (offCount and 0xFF).toByte(),
                    ((offCount shr 8) and 0x0F).toByte(),
                )
            }
        }
    }

    companion object {
        /** Wire a real chip up through the daemon-backed [com.krillforge.pi4j.I2cClient]. */
        fun over(
            i2c: com.krillforge.pi4j.I2cClient,
            bus: Int,
            address: Int,
            oscillatorHz: Double = OSCILLATOR_HZ_NOMINAL,
        ): Pca9685Client = Pca9685Client(i2c.toI2cBus(bus, address), oscillatorHz)

        /**
         * Software E-stop: every board still answering the default ALLCALL address (0x70)
         * goes dark in one 3-byte write, regardless of its own configured address.
         * Pass an [I2cBus] bound to [Pca9685Registers.ALL_CALL_ADDRESS_DEFAULT].
         */
        suspend fun emergencyStopAll(allCallBus: I2cBus) {
            allCallBus.writeRegister(ALL_LED_OFF_H, LED_FULL_BIT)
        }
    }
}
