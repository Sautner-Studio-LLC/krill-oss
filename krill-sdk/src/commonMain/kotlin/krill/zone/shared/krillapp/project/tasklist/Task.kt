/**
 * One row of a `Project.TaskList` node — a single user-defined task. Lives
 * inside [TaskListMetaData.tasks]; the list node owns ordering, completion
 * tracking, and recurrence semantics.
 */
package krill.zone.shared.krillapp.project.tasklist

import kotlinx.serialization.*

/**
 * A task entry inside a TaskList.
 *
 * `id` is generated client-side at creation time so the editor can reorder
 * tasks optimistically without round-tripping. `recurrence` is a cron
 * expression — empty means a one-shot task; a non-empty value causes the
 * server-side `TaskListExpiryTask` to re-arm the task on its schedule.
 */
@Serializable
data class Task(
    /** Stable per-task UUID — used for reordering and re-emission. */
    val id: String = "",
    /** User-typed task body. */
    val description: String = "",
    /** `true` once the user has checked the task off. */
    val isCompleted: Boolean = false,
    /** Optional due date as epoch millis — `null` if the task has no deadline. */
    val dueDate: Long? = null,
    /** Cron expression for recurring tasks. Empty string for one-shot tasks. */
    val recurrence: String = "",
    /**
     * `true` after the server's expiry tick has fired children for this task at least
     * once — prevents duplicate child execution if multiple ticks see the same expired task.
     */
    val expiredExecuted: Boolean = false,
    /** Epoch millis the user checked the task. `0` means "never completed". */
    val completedOn: Long = 0L,
)
