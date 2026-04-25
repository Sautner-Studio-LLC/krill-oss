/**
 * Quantised lookback windows used by Krill's `Compute` executor and by the
 * `Graph` data-point — both ask the server for "the last N units of data" and
 * agree on the supported step sizes via this enum.
 *
 * Centralising the windows here means the UI dropdown, the time-axis
 * formatter, and the H2 query layer all share one set of values; adding a new
 * window (e.g. `QUARTER`) only needs touching this file plus the few `when`
 * branches that consume it.
 */
package krill.zone.shared.krillapp.executor.compute

import kotlin.time.*

/**
 * Predefined lookback windows for time-series queries.
 *
 * `NONE` is the explicit "don't filter by time" sentinel — used when the
 * caller wants the whole series and downstream code should pass `0` as the
 * lower bound rather than computing a window.
 */
enum class ComputeTimeRange {
    MINUTE, HOUR, DAY, WEEK, MONTH, YEAR, NONE;
}

/**
 * Returns the lower bound of the lookback window in epoch milliseconds —
 * i.e. `now - window`. For [ComputeTimeRange.NONE] returns `0L` so the caller
 * can pass it as "no lower bound" in a SQL query.
 *
 * Month is treated as 30 days, year as 365 — calendar-aware accounting is
 * deliberately not done here because the consumers (graph axes, compute
 * windows) need a stable, predictable step size, not human-readable months.
 */
@OptIn(ExperimentalTime::class)
fun ComputeTimeRange.getTime(): Long {
    val t = Clock.System.now().toEpochMilliseconds()
    return when (this) {
        ComputeTimeRange.MINUTE -> t - 60 * 1000
        ComputeTimeRange.HOUR -> t - 60 * 60 * 1000
        ComputeTimeRange.DAY -> t - 24 * 60 * 60 * 1000
        ComputeTimeRange.WEEK -> t - 7 * 24 * 60 * 60 * 1000
        ComputeTimeRange.MONTH -> t - 30L * 24 * 60 * 60 * 1000
        ComputeTimeRange.YEAR -> t - 365L * 24 * 60 * 60 * 1000
        ComputeTimeRange.NONE -> 0
    }
}

/**
 * Renders the enum name in title case for display in the editor dropdown
 * (`MINUTE` → `"Minute"`, `WEEK` → `"Week"`).
 */
fun ComputeTimeRange.title(): String {
    return name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
