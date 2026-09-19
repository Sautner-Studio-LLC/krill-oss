package com.krillforge.pi4j.servo

/**
 * Backend-agnostic [Servo]: runs every command through [ServoMath]'s composition and hands the
 * resulting pulse width to [writePulseUs]. Talks to the wire in microseconds only — a PCA9685 (or
 * any other 50 Hz PWM backend) converts that to duty ticks at the point it writes the register.
 *
 * [clockMs] is an injected seam (not `System.currentTimeMillis()` baked in) so rate-limiting can
 * be driven by a fake clock in tests instead of real elapsed time.
 */
class PulseServo(
    initialProfile: ServoProfile,
    private val writePulseUs: suspend (Int?) -> Unit,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) : Servo {

    private var profile: ServoProfile = initialProfile
    private var lastUpdateMs: Long? = null

    /** Best-effort position estimate, always defined — assumed centered until the first real command. */
    private var trackedDeg: Double = ServoMath.effectiveLimits(initialProfile).let { (min, max) -> (min + max) / 2.0 }

    override var commandedDeg: Double? = null
        private set

    override suspend fun angle(deg: Double): ServoCommandResult {
        val finalDeg = ServoMath.reverseAndTrim(profile, deg)
        return applyFinalDeg(requestedDeg = deg, finalDeg = finalDeg)
    }

    override suspend fun pulse(us: Int): ServoCommandResult {
        // Pulse is already in the wire's final frame — re-limited (step 3 onward), but not
        // re-reversed/re-trimmed, which are operator-frame-only concerns for angle().
        val finalDeg = ServoMath.pulseUsToAngle(profile, us)
        return applyFinalDeg(requestedDeg = finalDeg, finalDeg = finalDeg)
    }

    override suspend fun park(): ServoCommandResult {
        val parkUs = profile.parkPulseUs
            ?: return disarm(reason = "park: no parkPulseUs configured, torque off")
        val finalDeg = ServoMath.pulseUsToAngle(profile, parkUs)
        return applyFinalDeg(requestedDeg = finalDeg, finalDeg = finalDeg)
    }

    override suspend fun torqueOff(): ServoCommandResult = disarm(reason = "torqueOff")

    override suspend fun retrim(trimDeg: Double): ServoCommandResult {
        profile = profile.copy(trimDeg = trimDeg)
        return commandedDeg?.let { angle(it) }
            ?: ServoCommandResult.Applied(requestedDeg = trimDeg, appliedDeg = trackedDeg, pulseUs = ServoMath.angleToPulseUs(profile, trackedDeg))
    }

    override suspend fun reprofile(profile: ServoProfile): ServoCommandResult {
        this.profile = profile
        return commandedDeg?.let { angle(it) }
            ?: ServoCommandResult.Applied(requestedDeg = trackedDeg, appliedDeg = trackedDeg, pulseUs = ServoMath.angleToPulseUs(profile, trackedDeg))
    }

    private suspend fun applyFinalDeg(requestedDeg: Double, finalDeg: Double): ServoCommandResult {
        val now = clockMs()
        val dtSeconds = lastUpdateMs?.let { (now - it) / 1000.0 } ?: Double.POSITIVE_INFINITY

        val (clamped, limit) = ServoMath.clamp(profile, finalDeg)
        val rateLimited = ServoMath.rateLimit(trackedDeg, clamped, profile.maxRateDegPerSec, dtSeconds)
        val pulseUs = ServoMath.angleToPulseUs(profile, rateLimited)

        writePulseUs(pulseUs)

        lastUpdateMs = now
        trackedDeg = rateLimited
        commandedDeg = requestedDeg

        return when {
            limit != null -> ServoCommandResult.Clamped(requestedDeg, limit, rateLimited, pulseUs)
            rateLimited != clamped -> ServoCommandResult.RateLimited(requestedDeg, rateLimited, pulseUs)
            else -> ServoCommandResult.Applied(requestedDeg, rateLimited, pulseUs)
        }
    }

    private suspend fun disarm(reason: String): ServoCommandResult.Disarmed {
        writePulseUs(null)
        lastUpdateMs = clockMs()
        return ServoCommandResult.Disarmed(reason, trackedDeg)
    }
}
