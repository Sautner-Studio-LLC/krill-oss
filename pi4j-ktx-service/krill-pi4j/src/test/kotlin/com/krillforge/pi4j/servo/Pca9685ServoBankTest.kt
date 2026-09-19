package com.krillforge.pi4j.servo

import com.krillforge.pi4j.pca9685.FakeI2cBus
import com.krillforge.pi4j.pca9685.Pca9685Client
import com.krillforge.pi4j.pca9685.Pca9685Registers.LED0_ON_L
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class Pca9685ServoBankTest {

    private val profile = ServoProfile(angleMinDeg = 0.0, angleMaxDeg = 180.0, endStopBackoffDeg = 0.0)

    @Test
    fun `setAngles writes every channel in a single block write`() = runTest {
        val bus = FakeI2cBus()
        val bank = Pca9685ServoBank(
            Pca9685Client(bus),
            mapOf(0 to profile, 1 to profile),
            frequencyHz = 50.0,
            scope = this,
            clockMs = { currentTime },
        )

        bank.setAngles(mapOf(0 to 90.0, 1 to 45.0))

        assertEquals(1, bus.blockWrites.size)
        val (register, frame) = bus.blockWrites.single()
        assertEquals(LED0_ON_L, register)
        assertEquals(64, frame.size)
    }

    @Test
    fun `setAngles leaves channels not addressed this call untouched`() = runTest {
        val bus = FakeI2cBus()
        val bank = Pca9685ServoBank(
            Pca9685Client(bus),
            mapOf(0 to profile, 1 to profile),
            frequencyHz = 50.0,
            scope = this,
            clockMs = { currentTime },
        )

        bank.setAngles(mapOf(0 to 90.0, 1 to 90.0))
        val (_, firstFrame) = bus.blockWrites.single()
        val channel1Bytes = firstFrame.copyOfRange(4, 8)

        bank.setAngles(mapOf(0 to 45.0)) // channel 1 not addressed this time
        val (_, secondFrame) = bus.blockWrites.last()

        assertEquals(channel1Bytes.toList(), secondFrame.copyOfRange(4, 8).toList())
    }

    @Test
    fun `torqueOffAll writes full-off to every configured channel in one transaction`() = runTest {
        val bus = FakeI2cBus()
        val bank = Pca9685ServoBank(
            Pca9685Client(bus),
            mapOf(0 to profile, 1 to profile),
            frequencyHz = 50.0,
            scope = this,
            clockMs = { currentTime },
        )
        bank.setAngles(mapOf(0 to 90.0, 1 to 90.0))
        bus.blockWrites.clear()

        bank.torqueOffAll()

        val (register, frame) = bus.blockWrites.single()
        assertEquals(LED0_ON_L, register)
        // full-off encoding per channel is 0,0,0,LED_FULL_BIT (see Pca9685Client.packChannel).
        assertEquals(listOf<Byte>(0, 0, 0, 0x10), frame.copyOfRange(0, 4).toList())
        assertEquals(listOf<Byte>(0, 0, 0, 0x10), frame.copyOfRange(4, 8).toList())
    }

    @Test
    fun `watchdog torques off everything once the timeout elapses with no update`() = runTest {
        val bus = FakeI2cBus()
        val bank = Pca9685ServoBank(
            Pca9685Client(bus),
            mapOf(0 to profile),
            frequencyHz = 50.0,
            scope = backgroundScope,
            clockMs = { currentTime },
        )
        bank.setAngles(mapOf(0 to 90.0))
        bus.blockWrites.clear()

        bank.startWatchdog(watchdogMs = 100)
        advanceTimeBy(150)
        runCurrent()

        val (_, frame) = bus.blockWrites.single()
        assertEquals(listOf<Byte>(0, 0, 0, 0x10), frame.copyOfRange(0, 4).toList())
    }

    @Test
    fun `watchdog does not fire while setAngles keeps updating within the window`() = runTest {
        val bus = FakeI2cBus()
        val bank = Pca9685ServoBank(
            Pca9685Client(bus),
            mapOf(0 to profile),
            frequencyHz = 50.0,
            scope = backgroundScope,
            clockMs = { currentTime },
        )
        bank.setAngles(mapOf(0 to 90.0))
        bank.startWatchdog(watchdogMs = 100)
        bus.blockWrites.clear()

        repeat(3) {
            advanceTimeBy(60)
            runCurrent()
            bank.setAngles(mapOf(0 to 90.0)) // heartbeat before the 100ms window elapses
        }

        // Every recorded write is a live setAngles frame (0x0B84 = 1500us at 50Hz -> ~307 ticks
        // on), never the full-off torque pattern.
        assertEquals(true, bus.blockWrites.isNotEmpty())
        assertEquals(true, bus.blockWrites.none { (_, frame) -> frame.copyOfRange(0, 4).toList() == listOf<Byte>(0, 0, 0, 0x10) })
    }
}
