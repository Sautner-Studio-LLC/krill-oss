/**
 * Serial-port data-bits configuration. The numeric [value] field matches the
 * jSerialComm constant the server uses to configure the underlying port.
 */
package krill.zone.shared.krillapp.server.serialdevice

/**
 * Number of data bits per serial frame (5 / 6 / 7 / 8).
 */
enum class SerialDataBits(val value: Int, val displayName: String) {
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    ;

    companion object {
        /** Returns the entry whose [value] matches, falling back to `EIGHT` for unknown input. */
        fun fromValue(value: Int): SerialDataBits = entries.find { it.value == value } ?: EIGHT
    }
}
