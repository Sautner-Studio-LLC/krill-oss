/**
 * Serial-port parity configuration. The numeric [value] field matches the
 * jSerialComm `SerialPort.NO_PARITY` / `ODD_PARITY` / `EVEN_PARITY` /
 * `MARK_PARITY` / `SPACE_PARITY` constants.
 */
package krill.zone.shared.krillapp.server.serialdevice

/**
 * Parity bit configuration on the serial line.
 */
enum class SerialParity(val value: Int, val displayName: String) {
    NONE(0, "None"),
    ODD(1, "Odd"),
    EVEN(2, "Even"),
    MARK(3, "Mark"),
    SPACE(4, "Space"),
    ;

    companion object {
        /** Returns the entry whose [value] matches, falling back to `NONE` for unknown input. */
        fun fromValue(value: Int): SerialParity = entries.find { it.value == value } ?: NONE
    }
}
