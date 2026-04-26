/**
 * Serial-port flow-control configuration. The numeric [value] field matches
 * jSerialComm flag combinations:
 *
 *  * `0` — `FLOW_CONTROL_DISABLED`
 *  * `48` — `FLOW_CONTROL_RTS_ENABLED | FLOW_CONTROL_CTS_ENABLED`
 *  * `12` — `FLOW_CONTROL_XONXOFF_IN_ENABLED | FLOW_CONTROL_XONXOFF_OUT_ENABLED`
 */
package krill.zone.shared.krillapp.server.serialdevice

/**
 * Hardware / software flow-control mode.
 */
enum class SerialFlowControl(val value: Int, val displayName: String) {
    NONE(0, "None"),
    RTS_CTS(48, "RTS/CTS"),
    XON_XOFF(12, "XON/XOFF"),
    ;

    companion object {
        /** Returns the entry whose [value] matches, falling back to `NONE` for unknown input. */
        fun fromValue(value: Int): SerialFlowControl = entries.find { it.value == value } ?: NONE
    }
}
