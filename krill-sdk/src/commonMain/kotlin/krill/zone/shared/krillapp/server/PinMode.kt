/**
 * Direction / capability mode of a single GPIO pin assigned to a
 * `Server.Pin` node — output, input, or PWM. Selected at pin configuration
 * time and not changed at runtime; the pi4j daemon configures the underlying
 * hardware accordingly when the node is created.
 *
 * The enum class is named [Mode] (not `PinMode`) because it lives inside the
 * `krillapp.server` package — the file name follows the legacy convention.
 */
package krill.zone.shared.krillapp.server

import kotlinx.serialization.*

/**
 * GPIO pin direction / capability.
 *
 * `lookup` finds an entry by its display [label] — used by the editor's
 * dropdown when restoring a saved selection from a label string.
 */
@Serializable
enum class Mode(val label: String) {
    OUT("Digital Output"),
    IN("Digital Input"),
    PWM("PWM"),
    ;

    companion object {
        /** Returns the entry whose [label] matches, or `null` if no match. */
        fun lookup(label: String?): Mode? {
            if (label == null) return null
            return entries.find { it.label == label }
        }
    }
}
