package com.krillforge.pi4j.servo

/**
 * Per-unit calibration and safety envelope for one [Servo]. Angle is the only unit callers should
 * think in; [pulseMinUs]/[pulseMaxUs] exist purely to translate to/from the wire.
 *
 * [limitMinDeg]/[limitMaxDeg] are a *mechanism* property (how far the joint may safely travel);
 * [trimDeg] is a *unit* property (this individual horn's mechanical zero is off by a few degrees).
 * See [ServoMath] for why the two must never be applied in the wrong order.
 */
data class ServoProfile(
    val pulseMinUs: Int = 500,
    val pulseMaxUs: Int = 2500,
    val angleMinDeg: Double = 0.0,
    val angleMaxDeg: Double = 270.0,
    val reversed: Boolean = false,
    val trimDeg: Double = 0.0,
    val limitMinDeg: Double? = null,
    val limitMaxDeg: Double? = null,
    val endStopBackoffDeg: Double = 10.0,
    val parkPulseUs: Int? = null,
    val maxRateDegPerSec: Double? = null,
) {
    init {
        require(angleMinDeg < angleMaxDeg) { "angleMinDeg ($angleMinDeg) must be < angleMaxDeg ($angleMaxDeg)" }
        require(pulseMinUs < pulseMaxUs) { "pulseMinUs ($pulseMinUs) must be < pulseMaxUs ($pulseMaxUs)" }
        require(endStopBackoffDeg >= 0.0) { "endStopBackoffDeg must be >= 0, was $endStopBackoffDeg" }
        require(2 * endStopBackoffDeg < angleMaxDeg - angleMinDeg) {
            "endStopBackoffDeg ($endStopBackoffDeg) leaves no travel between angleMinDeg and angleMaxDeg"
        }
        if (limitMinDeg != null && limitMaxDeg != null) {
            require(limitMinDeg < limitMaxDeg) { "limitMinDeg ($limitMinDeg) must be < limitMaxDeg ($limitMaxDeg)" }
        }
        require(maxRateDegPerSec == null || maxRateDegPerSec > 0.0) {
            "maxRateDegPerSec must be positive when set, was $maxRateDegPerSec"
        }
    }
}
