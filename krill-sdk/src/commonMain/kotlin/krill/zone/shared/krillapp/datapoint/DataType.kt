/**
 * Coarse type tag carried on every Krill `DataPoint`. Drives both the editor
 * UI (which input control to show, which filters/triggers are relevant — see
 * `DataPointRelevance`) and the runtime semantics of [Snapshot] values
 * (numeric vs. textual, ordered vs. unordered).
 *
 * Was previously co-located with `DataPointMetaData`; lifted into its own SDK
 * file so consumers can reference the type without dragging the (still
 * shared-only) full metadata class.
 */
package krill.zone.shared.krillapp.datapoint

/**
 * The set of value semantics a DataPoint can carry.
 *
 * Ordinals and names are part of the wire contract — added entries must go
 * at the end so older clients deserialise to a known value (or fail loud).
 */
enum class DataType {
    /** Free-form text. Snapshots store the raw string. */
    TEXT,

    /** Structured JSON document, also serialised as a string. */
    JSON,

    /** Binary on/off — see [krill.zone.shared.node.DigitalState]. */
    DIGITAL,

    /** Continuous numeric value. Filters and graphs operate on this type. */
    DOUBLE,

    /** Packed RGB colour, encoded as an integer in the snapshot value. */
    COLOR,
}
