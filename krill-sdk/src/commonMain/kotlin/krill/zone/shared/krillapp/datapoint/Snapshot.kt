/**
 * The atomic unit of time-series data in Krill: one [Snapshot] = one
 * (timestamp, value) pair captured by a `DataPoint` node. Snapshots flow
 * through the swarm as the payload of `SNAPSHOT_UPDATE` events, are persisted
 * to the server's H2 database, and replay back to clients via the `/data/series`
 * HTTP endpoint and SSE stream.
 *
 * The value is always carried as a trimmed `String` so a single type can host
 * every Krill data type (`DOUBLE`, `DIGITAL`, `COLOR`, `TEXT`, `JSON`).
 * Numeric consumers should use [doubleValue] for tolerant parsing.
 */
package krill.zone.shared.krillapp.datapoint

import kotlinx.serialization.*

/**
 * Immutable (timestamp, value) tuple representing one observation captured by
 * a DataPoint.
 *
 * The constructor is private so the two factory invocations on [Companion]
 * own the trimming / numeric-formatting policy:
 *
 *  * `Snapshot(ts, "raw text")` — strings are trimmed of incidental whitespace.
 *  * `Snapshot(ts, 42.5)` — doubles are stringified via Kotlin's default
 *    formatter (`"42.5"`), preserving precision for round-trip parsing.
 */
@ConsistentCopyVisibility
@Serializable
data class Snapshot private constructor(
    /** Capture time, epoch milliseconds. `0L` means "no observation yet". */
    val timestamp: Long,
    /**
     * Stringly-typed value. Numeric DataPoints should round-trip via
     * `value.toDouble()` (see [doubleValue]); other DataTypes (TEXT, JSON,
     * DIGITAL, COLOR) carry their natural string form.
     */
    val value: String,
) {
    companion object {
        /** Build a snapshot whose value is text — trimmed of leading/trailing whitespace. */
        operator fun invoke(timestamp: Long = 0L, value: String = ""): Snapshot =
            Snapshot(timestamp, value.trim())

        /** Build a snapshot whose value is numeric — stringified for the wire. */
        operator fun invoke(timestamp: Long, value: Double = 0.0): Snapshot =
            Snapshot(timestamp, "$value")
    }
}

/**
 * Returns this snapshot's [Snapshot.value] parsed as a `Double`, or `0.0` if
 * the value isn't a parseable number. Defensive default keeps downstream math
 * (graphs, calculations, threshold triggers) free of `NumberFormatException`
 * handling boilerplate.
 */
fun Snapshot.doubleValue(): Double {
    return try {
        value.toDouble()
    } catch (_: NumberFormatException) {
        0.0
    }
}
