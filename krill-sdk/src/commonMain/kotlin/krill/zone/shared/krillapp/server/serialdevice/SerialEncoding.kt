/**
 * Character-encoding selector for a `Server.SerialDevice`. Drives both the
 * decoding of bytes received from the device and the encoding of outbound
 * commands.
 *
 * `BINARY` is a special case: the on-the-wire bytes are not re-interpreted,
 * just round-tripped through the `ISO-8859-1` charset (a 1-byte ↔ 1-codepoint
 * mapping) so byte-level fidelity is preserved when working with a raw
 * binary protocol.
 */
package krill.zone.shared.krillapp.server.serialdevice

/**
 * Charset used to decode / encode serial traffic.
 */
enum class SerialEncoding(val charsetName: String, val displayName: String) {
    ASCII("US-ASCII", "ASCII"),
    UTF_8("UTF-8", "UTF-8"),
    ISO_8859_1("ISO-8859-1", "ISO-8859-1 (Latin-1)"),
    BINARY("ISO-8859-1", "Binary (raw bytes)"),
    ;

    companion object {
        /** Returns the entry whose [charsetName] matches, falling back to `UTF_8` for unknown input. */
        fun fromCharsetName(name: String): SerialEncoding = entries.find { it.charsetName == name } ?: UTF_8
    }
}
