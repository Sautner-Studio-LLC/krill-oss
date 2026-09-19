package com.krillforge.pi4j.pca9685

import com.krillforge.pi4j.proto.PinState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Pca9685BoardTest {

    @Test
    fun `OE read maps LOW to armed and HIGH to disarmed`() {
        // The whole point of this test: getting this backwards arms the outputs on every fault.
        assertTrue(isOeLow(PinState.PIN_STATE_LOW))
        assertFalse(isOeLow(PinState.PIN_STATE_HIGH))
    }

    @Test
    fun `isArmed defers to the OE reader when one is wired`() = runTest {
        val client = Pca9685Client(FakeI2cBus())

        val armed = Pca9685Board(client) { true }
        val disarmed = Pca9685Board(client) { false }

        assertEquals(true, armed.isArmed())
        assertEquals(false, disarmed.isArmed())
    }

    @Test
    fun `isArmed defaults to true when OE has no monitored GPIO`() = runTest {
        val board = Pca9685Board(Pca9685Client(FakeI2cBus()))

        assertTrue(board.isArmed())
    }
}
