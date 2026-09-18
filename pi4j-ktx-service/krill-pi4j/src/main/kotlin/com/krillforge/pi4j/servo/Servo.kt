package com.krillforge.pi4j.servo

/**
 * One servo — hobby servo, sail winch, camera gimbal, robot joint, ESC arming. Angle or
 * microseconds only; never duty-percent (see [ServoMath] for why: percent can't carry the
 * resolution a servo needs, and nobody thinks in duty cycle at 50 Hz).
 */
interface Servo {
    /** The last angle actually commanded; `null` before the first [angle]/[pulse] call. Servos
     *  have no position feedback, so this is memory of what we asked for, never a read. */
    val commandedDeg: Double?

    /** Command an angle in the operator's frame (i.e. before [ServoProfile.reversed]/[ServoProfile.trimDeg]). */
    suspend fun angle(deg: Double): ServoCommandResult

    /** Command a raw pulse width. Converts to an angle and re-enters the same limit/rate pipeline
     *  as [angle] — there is deliberately no bypass. */
    suspend fun pulse(us: Int): ServoCommandResult

    /** Move to [ServoProfile.parkPulseUs] if configured, otherwise stop pulsing (torque off). */
    suspend fun park(): ServoCommandResult

    /** Stop pulsing immediately. Idempotent — safe to call repeatedly, e.g. from a watchdog. */
    suspend fun torqueOff(): ServoCommandResult

    /** Live per-unit zero-offset calibration; reapplies the last commanded angle under the new trim. */
    suspend fun retrim(trimDeg: Double): ServoCommandResult

    /** Swap the whole profile; reapplies the last commanded angle under the new profile. */
    suspend fun reprofile(profile: ServoProfile): ServoCommandResult
}
