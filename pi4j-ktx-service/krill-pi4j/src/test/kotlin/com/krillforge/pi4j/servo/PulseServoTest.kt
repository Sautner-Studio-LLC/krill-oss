package com.krillforge.pi4j.servo

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A fake, mutable clock -- no real elapsed time, so rate-limiting is deterministic. */
private class FakeClock(private var nowMs: Long = 0L) {
    fun advance(ms: Long) {
        nowMs += ms
    }

    operator fun invoke(): Long = nowMs
}

private class RecordingWriter {
    val writes = mutableListOf<Int?>()
    val write: suspend (Int?) -> Unit = { us -> writes.add(us) }
}

class PulseServoTest {

    private val profile = ServoProfile(
        pulseMinUs = 500,
        pulseMaxUs = 2500,
        angleMinDeg = 0.0,
        angleMaxDeg = 180.0,
        endStopBackoffDeg = 10.0,
    )

    @Test
    fun `angle before any command is not rate limited and reports Applied`() = runTest {
        val writer = RecordingWriter()
        val servo = PulseServo(profile, writer.write)

        val result = servo.angle(90.0)

        assertIs<ServoCommandResult.Applied>(result)
        assertEquals(90.0, result.appliedDeg)
        assertEquals(1500, result.pulseUs)
        assertEquals(listOf<Int?>(1500), writer.writes)
        assertEquals(90.0, servo.commandedDeg)
    }

    @Test
    fun `angle out of the effective window reports Clamped with the requested-vs-applied pair`() = runTest {
        val servo = PulseServo(profile, RecordingWriter().write)

        val result = servo.angle(1000.0)

        assertIs<ServoCommandResult.Clamped>(result)
        assertEquals(1000.0, result.requestedDeg)
        assertEquals(Limit.MAX, result.limit)
        assertEquals(170.0, result.appliedDeg) // angleMax(180) - backoff(10)
    }

    @Test
    fun `pulse is limited identically to the equivalent angle call`() = runTest {
        val viaAngle = PulseServo(profile, RecordingWriter().write)
        val viaPulse = PulseServo(profile, RecordingWriter().write)

        val requestedDeg = 1000.0 // well past the effective max
        val equivalentUs = ServoMath.angleToPulseUs(profile, requestedDeg)

        val angleResult = viaAngle.angle(requestedDeg)
        val pulseResult = viaPulse.pulse(equivalentUs)

        assertIs<ServoCommandResult.Clamped>(angleResult)
        assertIs<ServoCommandResult.Clamped>(pulseResult)
        assertEquals(angleResult.appliedDeg, pulseResult.appliedDeg)
        assertEquals(angleResult.pulseUs, pulseResult.pulseUs)
    }

    @Test
    fun `rate limiting caps travel across a known dt, then reports Applied once caught up`() = runTest {
        val clock = FakeClock()
        val limited = profile.copy(maxRateDegPerSec = 90.0)
        val writer = RecordingWriter()
        val servo = PulseServo(limited, writer.write, clockMs = clock::invoke)

        servo.angle(50.0) // first move is never rate limited (no prior sample); well inside limits
        clock.advance(100) // 0.1s at 90 deg/s -> 9 degrees of travel allowed

        val limitedResult = servo.angle(90.0)
        assertIs<ServoCommandResult.RateLimited>(limitedResult)
        assertEquals(59.0, limitedResult.appliedDeg, absoluteTolerance = 1e-9) // 50 + 9

        clock.advance(10_000) // plenty of time to finish the move
        val caughtUp = servo.angle(90.0)
        assertIs<ServoCommandResult.Applied>(caughtUp)
        assertEquals(90.0, caughtUp.appliedDeg)
    }

    @Test
    fun `torqueOff stops pulsing and is idempotent`() = runTest {
        val writer = RecordingWriter()
        val servo = PulseServo(profile, writer.write)
        servo.angle(45.0)

        val first = servo.torqueOff()
        val second = servo.torqueOff()

        assertIs<ServoCommandResult.Disarmed>(first)
        assertIs<ServoCommandResult.Disarmed>(second)
        assertEquals(first.appliedDeg, second.appliedDeg)
        assertEquals(listOf(1000, null, null), writer.writes) // 45deg pulse, then two torque-offs
    }

    @Test
    fun `park writes the configured park pulse when set`() = runTest {
        val parking = profile.copy(parkPulseUs = 1500) // mid-travel
        val writer = RecordingWriter()
        val servo = PulseServo(parking, writer.write)

        val result = servo.park()

        assertIs<ServoCommandResult.Applied>(result)
        assertEquals(1500, result.pulseUs)
    }

    @Test
    fun `park disarms when no park pulse is configured`() = runTest {
        val writer = RecordingWriter()
        val servo = PulseServo(profile, writer.write)

        val result = servo.park()

        assertIs<ServoCommandResult.Disarmed>(result)
        assertEquals(listOf<Int?>(null), writer.writes)
    }

    @Test
    fun `retrim reapplies the last commanded angle under the new trim`() = runTest {
        val writer = RecordingWriter()
        val servo = PulseServo(profile, writer.write)
        servo.angle(90.0)

        val result = servo.retrim(trimDeg = 5.0)

        assertIs<ServoCommandResult.Applied>(result)
        assertEquals(95.0, result.appliedDeg)
    }

    @Test
    fun `retrim before any command just records the trim and does not crash`() = runTest {
        val writer = RecordingWriter()
        val servo = PulseServo(profile, writer.write)

        val result = servo.retrim(trimDeg = 5.0)

        assertNull(servo.commandedDeg)
        assertTrue(writer.writes.isEmpty())
        assertEquals(result.appliedDeg, result.appliedDeg) // no crash constructing the result
    }
}

private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "expected $expected but was $actual (tolerance $absoluteTolerance)",
    )
}
