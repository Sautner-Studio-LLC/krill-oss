/**
 * Line terminator appended to commands sent on a `Server.SerialDevice`. Many
 * serial peripherals expect a CR / LF / CRLF terminator after each command;
 * a few (Atlas Scientific stamps, for example) accept raw bytes — pick `NONE`
 * for those.
 */
package krill.zone.shared.krillapp.server.serialdevice

/**
 * Trailing line-terminator byte sequence for outbound serial commands.
 *
 * The [value] is the literal byte sequence to append; the [displayName]
 * carries an editor-friendly form with escapes spelled out.
 */
enum class SerialTerminator(val value: String, val displayName: String) {
    CR("\r", "CR (\\r)"),
    LF("\n", "LF (\\n)"),
    CRLF("\r\n", "CRLF (\\r\\n)"),
    NONE("", "None"),
    ;

    companion object {
        /** Returns the entry whose [value] matches, falling back to `CR` for unknown input. */
        fun fromValue(value: String): SerialTerminator = entries.find { it.value == value } ?: CR
    }
}
