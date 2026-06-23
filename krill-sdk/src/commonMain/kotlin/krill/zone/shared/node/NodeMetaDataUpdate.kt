package krill.zone.shared.node

/**
 * Returns a copy of [meta] with its `error` field replaced by [error].
 *
 * Delegates to [NodeMetaData.withError], which every concrete MetaData
 * `data class` implements as `copy(error = error)`. No per-subtype dispatch
 * needed here — adding a new MetaData type requires only implementing the
 * abstract [NodeMetaData.withError] on that type, enforced at compile time.
 */
fun updateMetaWithError(meta: NodeMetaData, error: String): NodeMetaData = meta.withError(error)
