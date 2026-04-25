/**
 * Aggregation operations available to Krill's `Compute` executor.
 *
 * Each value names a single statistical reduction the compute engine knows
 * how to apply over a [ComputeTimeRange]-bounded series of [Snapshot]s.
 */
package krill.zone.shared.krillapp.executor.compute

/**
 * The set of aggregation reductions a Compute node can run.
 *
 * `NONE` is the "passthrough" sentinel — emit the raw value with no
 * aggregation, useful when wiring a Compute node purely for its time-window
 * gating without changing the value.
 */
enum class ComputeOperation {
    AVERAGE, MEAN, MEDIAN, HIGH, LOW, SUM, COUNT, RANGE, FIRST, LAST, STDDEV, NONE
}

/**
 * Renders the enum name in title case for display in the editor dropdown
 * (`AVERAGE` → `"Average"`, `STDDEV` → `"Stddev"`).
 */
fun ComputeOperation.title(): String {
    return name.lowercase().replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
}
