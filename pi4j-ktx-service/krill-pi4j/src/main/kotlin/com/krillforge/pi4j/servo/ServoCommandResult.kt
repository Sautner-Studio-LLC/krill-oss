package com.krillforge.pi4j.servo

/** Which side of the effective travel window a request was clamped against. */
enum class Limit { MIN, MAX }

/**
 * Outcome of one [Servo] command, returned as a value rather than logged, so a caller (e.g. a
 * motion planner) can react to a clamp or rate limit programmatically instead of it being silent.
 */
sealed interface ServoCommandResult {
    /** The angle now in effect after this command — unchanged from before for [Disarmed]/[Rejected]. */
    val appliedDeg: Double

    /** The request was honored exactly as asked. */
    data class Applied(
        val requestedDeg: Double,
        override val appliedDeg: Double,
        val pulseUs: Int,
    ) : ServoCommandResult

    /** The request was outside the effective travel window and was coerced to [limit]. */
    data class Clamped(
        val requestedDeg: Double,
        val limit: Limit,
        override val appliedDeg: Double,
        val pulseUs: Int,
    ) : ServoCommandResult

    /** The request was within limits but exceeded [ServoProfile.maxRateDegPerSec]; motion is staged. */
    data class RateLimited(
        val requestedDeg: Double,
        override val appliedDeg: Double,
        val pulseUs: Int,
    ) : ServoCommandResult

    /** The servo has stopped pulsing (torque off) — from [Servo.torqueOff] or an unparked [Servo.park]. */
    data class Disarmed(
        val reason: String,
        override val appliedDeg: Double,
    ) : ServoCommandResult

    /** The request was refused outright (e.g. an invalid [ServoProfile]); nothing changed. */
    data class Rejected(
        val requestedDeg: Double,
        val reason: String,
        override val appliedDeg: Double,
    ) : ServoCommandResult
}
