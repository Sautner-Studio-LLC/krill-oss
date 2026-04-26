/**
 * Serial-port stop-bits configuration. The numeric [value] field matches the
 * jSerialComm `SerialPort.ONE_STOP_BIT` (1) / `ONE_POINT_FIVE_STOP_BITS` (2) /
 * `TWO_STOP_BITS` (3) constants.
 */
package krill.zone.shared.krillapp.server.serialdevice

/**
 * Number of stop bits per serial frame.
 */
enum class SerialStopBits(val value: Int, val displayName: String) {
    ONE(1, "1"),
    ONE_POINT_FIVE(2, "1.5"),
    TWO(3, "2"),
    ;

    companion object {
        /** Returns the entry whose [value] matches, falling back to `ONE` for unknown input. */
        fun fromValue(value: Int): SerialStopBits = entries.find { it.value == value } ?: ONE
    }
}
