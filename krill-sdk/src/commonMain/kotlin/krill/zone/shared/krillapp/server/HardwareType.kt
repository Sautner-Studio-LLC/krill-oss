/**
 * Classification of a single header pin on the Raspberry Pi (or any
 * compatible header) by the kind of signal it carries. Used by the editor's
 * pin map to colour-code the header, and to decide which pins are eligible
 * for a `Server.Pin` node assignment.
 */
package krill.zone.shared.krillapp.server

/**
 * Hardware capability for a header position.
 *
 * The constructor parameters carry display state, not behaviour — [label] is
 * the human-readable name, [rgb] is a 24-bit colour used by the pin-map
 * renderer. The [color] computed property OR's the alpha channel in so the
 * value can be passed straight to a Compose `Color`.
 */
enum class HardwareType(val label: String, val rgb: Int) {
    POWER("Power", 0x990000),
    GROUND("Ground", 0x000000),
    DIGITAL("Digital", 0x009900),
    DIGITAL_AND_PWM("Digital and PWM", 0xff7f00),
    DIGITAL_NO_PULL_DOWN("Digital without pulldown", 0x800080),
    USB("USB", 0x990000),
    IC2("IC2", 0x800080),
    HAT("HAT", 0x700000),
    SERIAL("Serial", 0x700000),
    ;

    /** Returns [rgb] with the alpha channel set to 0xFF — suitable for `Color(color)`. */
    val color: Int
        get() = rgb or 0xFF000000.toInt()
}
