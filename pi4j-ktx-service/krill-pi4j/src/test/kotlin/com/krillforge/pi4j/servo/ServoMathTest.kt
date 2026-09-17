package com.krillforge.pi4j.servo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServoMathTest {

    private val profile = ServoProfile(
        pulseMinUs = 500,
        pulseMaxUs = 2500,
        angleMinDeg = 0.0,
        angleMaxDeg = 180.0,
        endStopBackoffDeg = 10.0,
    )

    // ── step 1+2: reverse before trim ───────────────────────────────────────────

    @Test
    fun `reverse mirrors across the profile's own angle range`() {
        val reversed = profile.copy(reversed = true)
        assertEquals(180.0, ServoMath.reverseAndTrim(reversed, 0.0))
        assertEquals(0.0, ServoMath.reverseAndTrim(reversed, 180.0))
        assertEquals(90.0, ServoMath.reverseAndTrim(reversed, 90.0)) // symmetric midpoint is a fixed point
    }

    @Test
    fun `trim is applied after reverse, so flipping reversed does not negate trim`() {
        val trimmed = profile.copy(trimDeg = 5.0)
        val trimmedReversed = profile.copy(trimDeg = 5.0, reversed = true)

        // Not reversed: 90 -> 95.
        assertEquals(95.0, ServoMath.reverseAndTrim(trimmed, 90.0))
        // Reversed: mirror(90) = 90, then +5 = 95 -- same trim direction, not negated.
        assertEquals(95.0, ServoMath.reverseAndTrim(trimmedReversed, 90.0))
    }

    @Test
    fun `no reverse, no trim is the identity`() {
        assertEquals(42.0, ServoMath.reverseAndTrim(profile, 42.0))
    }

    // ── step 3: limits, and the ordering with trim ──────────────────────────────

    @Test
    fun `effective limits shrink from both ends by the end-stop backoff`() {
        val (min, max) = ServoMath.effectiveLimits(profile)
        assertEquals(10.0, min)
        assertEquals(170.0, max)
    }

    @Test
    fun `an explicit limit narrows further but never loosens past the backoff`() {
        val tighter = profile.copy(limitMinDeg = 20.0, limitMaxDeg = 150.0)
        assertEquals(20.0 to 150.0, ServoMath.effectiveLimits(tighter))

        // A limit more permissive than the backoff-protected range does not loosen it.
        val looser = profile.copy(limitMinDeg = 0.0, limitMaxDeg = 180.0)
        assertEquals(10.0 to 170.0, ServoMath.effectiveLimits(looser))
    }

    @Test
    fun `trim cannot push a joint past its software limit`() {
        val limited = profile.copy(limitMaxDeg = 100.0, trimDeg = 5.0)
        // Request lands exactly on the limit before trim; trim then pushes it 5 degrees over.
        val trimmedDeg = ServoMath.reverseAndTrim(limited, 100.0)
        assertEquals(105.0, trimmedDeg)

        val (clamped, limit) = ServoMath.clamp(limited, trimmedDeg)
        assertEquals(100.0, clamped)
        assertEquals(Limit.MAX, limit)
    }

    @Test
    fun `clamp reports no limit when the request is inside the effective window`() {
        val (clamped, limit) = ServoMath.clamp(profile, 90.0)
        assertEquals(90.0, clamped)
        assertNull(limit)
    }

    @Test
    fun `clamp coerces below-min requests to the effective min and reports MIN`() {
        val (clamped, limit) = ServoMath.clamp(profile, -5.0)
        assertEquals(10.0, clamped)
        assertEquals(Limit.MIN, limit)
    }

    // ── step 4: rate limiting ────────────────────────────────────────────────────

    @Test
    fun `rate limit caps distance travelled to rate times dt`() {
        // 90 deg/s for 0.1s = 9 degrees of travel allowed.
        val result = ServoMath.rateLimit(previousDeg = 0.0, targetDeg = 90.0, maxRateDegPerSec = 90.0, dtSeconds = 0.1)
        assertEquals(9.0, result, absoluteTolerance = 1e-9)
    }

    @Test
    fun `rate limit passes the target through once within reach`() {
        val result = ServoMath.rateLimit(previousDeg = 85.0, targetDeg = 90.0, maxRateDegPerSec = 90.0, dtSeconds = 1.0)
        assertEquals(90.0, result)
    }

    @Test
    fun `rate limit steps toward the target from either direction`() {
        val up = ServoMath.rateLimit(previousDeg = 0.0, targetDeg = 90.0, maxRateDegPerSec = 10.0, dtSeconds = 1.0)
        val down = ServoMath.rateLimit(previousDeg = 90.0, targetDeg = 0.0, maxRateDegPerSec = 10.0, dtSeconds = 1.0)
        assertEquals(10.0, up)
        assertEquals(80.0, down)
    }

    @Test
    fun `rate limit is a no-op with no configured rate or non-positive dt`() {
        assertEquals(90.0, ServoMath.rateLimit(0.0, 90.0, maxRateDegPerSec = null, dtSeconds = 1.0))
        assertEquals(90.0, ServoMath.rateLimit(0.0, 90.0, maxRateDegPerSec = 10.0, dtSeconds = 0.0))
        assertEquals(90.0, ServoMath.rateLimit(0.0, 90.0, maxRateDegPerSec = 10.0, dtSeconds = Double.POSITIVE_INFINITY))
    }

    // ── step 5+6: angle <-> pulse mapping ────────────────────────────────────────

    @Test
    fun `angle to pulse is a linear map across the full angle and pulse range`() {
        assertEquals(500, ServoMath.angleToPulseUs(profile, 0.0))
        assertEquals(2500, ServoMath.angleToPulseUs(profile, 180.0))
        assertEquals(1500, ServoMath.angleToPulseUs(profile, 90.0))
    }

    @Test
    fun `pulse to angle is the exact inverse of angle to pulse`() {
        for (deg in listOf(0.0, 45.0, 90.0, 135.0, 180.0)) {
            val us = ServoMath.angleToPulseUs(profile, deg)
            assertEquals(deg, ServoMath.pulseUsToAngle(profile, us), absoluteTolerance = 1e-9)
        }
    }

    @Test
    fun `pulse to ticks maps a full range pulse to the full tick range at 50Hz`() {
        // 20ms period at 50Hz -> 20000us; a 2500us pulse is 12.5% of the cycle -> 512 ticks.
        assertEquals(512, ServoMath.pulseUsToTicks(2500, frequencyHz = 50.0))
        assertEquals(0, ServoMath.pulseUsToTicks(null, frequencyHz = 50.0))
    }
}

private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
    kotlin.test.assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "expected $expected but was $actual (tolerance $absoluteTolerance)",
    )
}
