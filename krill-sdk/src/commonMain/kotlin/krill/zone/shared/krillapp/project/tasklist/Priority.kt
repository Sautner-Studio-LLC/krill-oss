/**
 * Priority enum used by `Project.TaskList` and (potentially) other task-like
 * nodes in the future. The display labels are human-readable and the
 * `lookup` helper restores an entry from a label string — this is what the
 * editor's dropdown saves and reads.
 */
package krill.zone.shared.krillapp.project.tasklist

import kotlinx.serialization.*

/**
 * Task / list priority level.
 *
 * Each entry carries a display [label]; [toString] returns that label so a
 * priority can be dropped straight into UI text without an extra lookup.
 */
@Serializable
enum class Priority(val label: String) {
    NONE("None"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    ;

    override fun toString(): String = label

    companion object {
        /** Returns the entry whose [label] matches, or `null` for unknown / null input. */
        fun lookup(label: String?): Priority? {
            if (label == null) return null
            return entries.find { it.label == label }
        }
    }
}
