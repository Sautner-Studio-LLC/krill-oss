/**
 * Pure helper that derives the worst [krill.zone.shared.node.NodeState]
 * for a `Project.TaskList` from its current task list and overall priority.
 *
 * The same function is invoked on the server (when persisting `node.state`
 * after the expiry tick) and on the client (when picking the chip colour
 * cold-launch, before the server has had a chance to update). Lifting it
 * into the SDK guarantees the two sides agree pixel-perfectly on the
 * escalation tier without one accidentally drifting.
 */
package krill.zone.shared.krillapp.project.tasklist

import krill.zone.shared.node.NodeState

/**
 * Returns the worst [NodeState] that should be displayed for a TaskList
 * containing the given [tasks] under the given [priority].
 *
 * "Expired and open" is what drives escalation; an open task counts as
 * expired when either:
 *  - it has a `dueDate` in the past (`dueDate != null && dueDate <= now`), or
 *  - the server scheduler already fired it (`expiredExecuted == true`) — the
 *    recurring case, where `dueDate` may be null because the next fire
 *    time was computed from the cron expression and never persisted.
 *
 * Tier rules:
 *  - no expired-and-open tasks: `NONE`
 *  - priority is `HIGH` and any expired-and-open task: `SEVERE`
 *  - fixed-date overdue > 7 days: `SEVERE`
 *  - fixed-date overdue 1–7 days: `WARN`
 *  - otherwise (including recurring-only expiries without a dueDate): `INFO`
 *
 * Returns early at `SEVERE` since no other tier can supersede it.
 */
fun computeTaskListState(tasks: List<Task>, priority: Priority, nowMillis: Long): NodeState {
    var worst = NodeState.NONE
    for (task in tasks) {
        if (task.isCompleted) continue

        val due = task.dueDate
        val overdueMillis: Long = when {
            // Scheduler fired — task is "dead" until user intervention. For recurring
            // tasks `dueDate` may be null, so fall back to 0 (INFO tier).
            task.expiredExecuted -> due?.let { nowMillis - it } ?: 0L
            // Fixed-date task whose deadline passed (scheduler may not have fired yet
            // inside the current 1s reconcile window).
            due != null && due <= nowMillis -> nowMillis - due
            else -> continue
        }
        val overdueDays = overdueMillis / MILLIS_PER_DAY

        val tier = when {
            priority == Priority.HIGH -> NodeState.SEVERE
            overdueDays > 7 -> NodeState.SEVERE
            overdueDays >= 1 -> NodeState.WARN
            else -> NodeState.INFO
        }

        if (severity(tier) > severity(worst)) worst = tier
        if (worst == NodeState.SEVERE) return worst
    }
    return worst
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

private fun severity(state: NodeState): Int = when (state) {
    NodeState.NONE -> 0
    NodeState.INFO -> 1
    NodeState.WARN -> 2
    NodeState.SEVERE -> 3
    else -> 0
}
