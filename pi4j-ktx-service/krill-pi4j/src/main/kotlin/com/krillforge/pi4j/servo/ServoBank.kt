package com.krillforge.pi4j.servo

/**
 * A group of [Servo]s sharing one multi-channel PWM backend (e.g. one PCA9685 board).
 *
 * [setAngles] writes every touched channel in a single transaction where the backend supports it,
 * so a partial failure never leaves some actuators moved and others not. [startWatchdog] is the
 * safety net for a controller that stops updating: torque-off is cheap and idempotent, so a
 * missed heartbeat should always fail toward "goes limp," never "stays wherever it was."
 */
interface ServoBank {
    /** Command angles for the given channels; channels not present are left untouched. */
    suspend fun setAngles(angles: Map<Int, Double>): Map<Int, ServoCommandResult>

    /** Torque off every channel in this bank, in one transaction where the backend supports it. */
    suspend fun torqueOffAll()

    /** Torque off every channel if [setAngles] hasn't been called within [watchdogMs]. */
    suspend fun startWatchdog(watchdogMs: Long = 100)
}
