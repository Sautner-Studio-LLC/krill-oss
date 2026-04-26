/**
 * Static lookup table mapping each [DataType] to the [krill.zone.shared.KrillApp]
 * filter and trigger subtypes that are meaningful when the source's value
 * carries that data type.
 *
 * Centralising the rules here means the editor's "wireable child" picker,
 * the LLM agent's planner, and any third-party tooling all agree on which
 * filters / triggers make sense for a given DataPoint without duplicating
 * the `when (dataType)` switches at every call site. Adding a new
 * `DataType` requires extending both `relevantFilters` and `relevantTriggers`
 * — the compiler's exhaustive-`when` check will surface the omission.
 */
package krill.zone.shared.krillapp.datapoint

import krill.zone.shared.KrillApp

/**
 * Per-[DataType] catalogue of relevant filter and trigger node types.
 */
object DataPointRelevance {

    /**
     * Returns the [KrillApp] filter subtypes that are meaningful for a
     * DataPoint of the given [dataType]. Empty for types where no filter
     * has a defined behaviour (e.g. TEXT / JSON, which don't have numeric
     * cutoffs).
     */
    fun relevantFilters(dataType: DataType): List<KrillApp> = when (dataType) {
        DataType.DOUBLE -> listOf(
            KrillApp.DataPoint.Filter.DiscardAbove,
            KrillApp.DataPoint.Filter.DiscardBelow,
            KrillApp.DataPoint.Filter.Debounce,
            KrillApp.DataPoint.Filter.Deadband,
        )
        DataType.COLOR,
        DataType.DIGITAL,
        DataType.TEXT,
        DataType.JSON -> emptyList()
    }

    /**
     * Returns the [KrillApp] trigger subtypes that are meaningful for a
     * DataPoint of the given [dataType]. `SilentAlarmMs` shows up across
     * most types because "haven't seen a value in X ms" is a universally
     * useful health check.
     */
    fun relevantTriggers(dataType: DataType): List<KrillApp> = when (dataType) {
        DataType.DOUBLE -> listOf(
            KrillApp.Trigger.HighThreshold,
            KrillApp.Trigger.LowThreshold,
            KrillApp.Trigger.SilentAlarmMs,
        )
        DataType.COLOR -> listOf(
            KrillApp.Trigger.Color,
            KrillApp.Trigger.SilentAlarmMs,
        )
        DataType.DIGITAL -> listOf(
            KrillApp.Trigger.SilentAlarmMs,
        )
        DataType.TEXT,
        DataType.JSON -> emptyList()
    }

    /** `true` if [app] appears in [relevantFilters] for [dataType]. */
    fun isRelevantFilter(dataType: DataType, app: KrillApp): Boolean =
        relevantFilters(dataType).contains(app)

    /** `true` if [app] appears in [relevantTriggers] for [dataType]. */
    fun isRelevantTrigger(dataType: DataType, app: KrillApp): Boolean =
        relevantTriggers(dataType).contains(app)
}
