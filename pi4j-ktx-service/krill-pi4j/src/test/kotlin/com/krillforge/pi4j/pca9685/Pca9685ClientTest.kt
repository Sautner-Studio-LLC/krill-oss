package com.krillforge.pi4j.pca9685

import com.krillforge.pi4j.pca9685.Pca9685Registers.ALL_LED_OFF_H
import com.krillforge.pi4j.pca9685.Pca9685Registers.LED0_ON_L
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE1
import com.krillforge.pi4j.pca9685.Pca9685Registers.MODE2
import com.krillforge.pi4j.pca9685.Pca9685Registers.PRE_SCALE
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Pca9685ClientTest {

    @Test
    fun `init writes MODE1, MODE2, the sleep dance for 50Hz, then forces every channel off`() = runTest {
        val bus = FakeI2cBus()
        Pca9685Client(bus).init(frequencyHz = 50.0)

        assertEquals(
            listOf(
                MODE1 to 0x21,       // AI | ALLCALL
                MODE2 to 0x04,       // OUTDRV, OUTNE=00
                MODE1 to 0x31,       // + SLEEP, RESTART cleared
                PRE_SCALE to 121,    // 50Hz -> prescale 121 (0x79)
                MODE1 to 0x21,       // SLEEP cleared
                MODE1 to 0xA1,       // + RESTART
                ALL_LED_OFF_H to 0x10,
            ),
            bus.registerWrites,
        )
    }

    @Test
    fun `setFrequency at 200Hz computes prescale 30 and restores RESTART`() = runTest {
        val bus = FakeI2cBus()
        Pca9685Client(bus).setFrequency(200.0)

        assertEquals(
            listOf(
                MODE1 to 0x10,      // SLEEP set from a MODE1 that started at 0
                PRE_SCALE to 30,    // 200Hz -> prescale 30 (0x1E)
                MODE1 to 0x00,      // SLEEP cleared
                MODE1 to 0x80,      // RESTART set
            ),
            bus.registerWrites,
        )
    }

    @Test
    fun `setFrequency rejects when the PRE_SCALE read-back does not match`() = runTest {
        val bus = FakeI2cBus(ignoreWritesTo = setOf(PRE_SCALE))
        val client = Pca9685Client(bus)

        assertFailsWith<IllegalStateException> { client.setFrequency(50.0) }
    }

    @Test
    fun `writeFrame staggers ON counts across the cycle and wraps OFF correctly`() = runTest {
        val bus = FakeI2cBus()
        val dutyTicks = IntArray(16) { 1000 }

        Pca9685Client(bus).writeFrame(dutyTicks)

        val (register, frame) = bus.blockWrites.single()
        assertEquals(LED0_ON_L, register)
        assertEquals(64, frame.size)

        // channel 0: on=0, off=1000 (0x3E8) -- no wrap
        assertEquals(
            listOf(0x00, 0x00, 0xE8, 0x03),
            frame.copyOfRange(0, 4).map { it.toInt() and 0xFF },
        )

        // channel 15: on=15*4096/16=3840 (0xF00), off=(3840+1000) mod 4096 = 744 (0x2E8) -- wraps
        assertEquals(
            listOf(0x00, 0x0F, 0xE8, 0x02),
            frame.copyOfRange(60, 64).map { it.toInt() and 0xFF },
        )
    }

    @Test
    fun `emergencyStopAll writes ALL_LED_OFF_H full-off to the given bus`() = runTest {
        val bus = FakeI2cBus()

        Pca9685Client.emergencyStopAll(bus)

        assertEquals(listOf(ALL_LED_OFF_H to 0x10), bus.registerWrites)
    }

    @Test
    fun `isAsleep reflects MODE1 SLEEP bit, the silent brownout signal`() = runTest {
        val awake = FakeI2cBus(initialRegisters = mapOf(MODE1 to 0x21))
        val asleep = FakeI2cBus(initialRegisters = mapOf(MODE1 to 0x11)) // power-on-reset default

        assertEquals(false, Pca9685Client(awake).isAsleep())
        assertEquals(true, Pca9685Client(asleep).isAsleep())
    }
}
