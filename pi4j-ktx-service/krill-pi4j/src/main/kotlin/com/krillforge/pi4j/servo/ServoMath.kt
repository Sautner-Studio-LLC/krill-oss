package com.krillforge.pi4j.servo

import com.krillforge.pi4j.pca9685.Pca9685Registers.TICKS_PER_CYCLE
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Pure angle <-> pulse composition, no hardware, no coroutines. Every step is exposed
 * independently so [PulseServo] can report *which* step altered a request instead of silently
 * doing something other than what was asked.
 *
 * Composition order (see the servo API design issue for the reasoning):
 * ```
 * 1. reverse   a1 = if (reversed) (angleMin + angleMax) - a else a
 * 2. trim      a2 = a1 + trimDeg
 * 3. limits    a3 = a2.coerceIn(effectiveMin, effectiveMax)   -> Clamped
 * 4. rate      a4 = rateLimit(a3, dt)                          -> RateLimited
 * 5. -> pulse  linear map across [angleMinDeg, angleMaxDeg]
 * ```
 * Reverse before trim: trim is "this unit's zero is off by N degrees" in the operator's frame:
 * applying it before reverse would silently negate every trim value if the unit is later
 * flipped. Trim before limits: limits protect the mechanism, trim describes the unit — limiting
 * first would let a trimmed request sail past the physical stop the limit exists to prevent.
 */
object ServoMath {

    /**
     * The travel window a request may land in: the end-stop backoff shrinks the physical range
     * from both ends, and an explicit [ServoProfile.limitMinDeg]/[ServoProfile.limitMaxDeg] may
     * shrink it further — but never loosen past the backoff-protected range.
     */
    fun effectiveLimits(profile: ServoProfile): Pair<Double, Double> {
        val backoffMin = profile.angleMinDeg + profile.endStopBackoffDeg
        val backoffMax = profile.angleMaxDeg - profile.endStopBackoffDeg
        val min = maxOf(backoffMin, profile.limitMinDeg ?: backoffMin)
        val max = minOf(backoffMax, profile.limitMaxDeg ?: backoffMax)
        return min to max
    }

    /** Steps 1-2: mirror across the profile's own range, then apply the unit's trim. */
    fun reverseAndTrim(profile: ServoProfile, requestedDeg: Double): Double {
        val reversed = if (profile.reversed) {
            (profile.angleMinDeg + profile.angleMaxDeg) - requestedDeg
        } else {
            requestedDeg
        }
        return reversed + profile.trimDeg
    }

    /** Step 3: coerce into [effectiveLimits], reporting which side (if any) fired. */
    fun clamp(profile: ServoProfile, deg: Double): Pair<Double, Limit?> {
        val (min, max) = effectiveLimits(profile)
        return when {
            deg < min -> min to Limit.MIN
            deg > max -> max to Limit.MAX
            else -> deg to null
        }
    }

    /**
     * Step 4: cap the distance travelled since the last command to what [maxRateDegPerSec]
     * (degrees/second) allows over [dtSeconds]. `null` rate or non-positive `dt` is a no-op —
     * a non-positive dt means "no prior sample to measure a rate against" (e.g. the first move).
     */
    fun rateLimit(previousDeg: Double, targetDeg: Double, maxRateDegPerSec: Double?, dtSeconds: Double): Double {
        if (maxRateDegPerSec == null || dtSeconds <= 0.0 || !dtSeconds.isFinite()) return targetDeg
        val maxStep = maxRateDegPerSec * dtSeconds
        val delta = targetDeg - previousDeg
        return if (abs(delta) <= maxStep) targetDeg else previousDeg + maxStep * sign(delta)
    }

    /** Step 5: linear map from the profile's full angle range to its pulse range, in microseconds. */
    fun angleToPulseUs(profile: ServoProfile, deg: Double): Int {
        val angleSpan = profile.angleMaxDeg - profile.angleMinDeg
        val fraction = (deg - profile.angleMinDeg) / angleSpan
        val pulse = profile.pulseMinUs + fraction * (profile.pulseMaxUs - profile.pulseMinUs)
        return pulse.roundToInt()
    }

    /** Inverse of [angleToPulseUs] — used by [Servo.pulse] to re-enter the pipeline at step 3. */
    fun pulseUsToAngle(profile: ServoProfile, us: Int): Double {
        val pulseSpan = profile.pulseMaxUs - profile.pulseMinUs
        val fraction = (us - profile.pulseMinUs).toDouble() / pulseSpan
        return profile.angleMinDeg + fraction * (profile.angleMaxDeg - profile.angleMinDeg)
    }

    /** Step 6: a pulse width in microseconds, at [frequencyHz], as PCA9685 duty ticks (0..4096). `null` = fully off. */
    fun pulseUsToTicks(us: Int?, frequencyHz: Double): Int {
        if (us == null) return 0
        val periodUs = 1_000_000.0 / frequencyHz
        return (us / periodUs * TICKS_PER_CYCLE).roundToInt().coerceIn(0, TICKS_PER_CYCLE)
    }
}
