/**
 * Plug-in contract for the side of Krill that watches every node's
 * [kotlinx.coroutines.flow.MutableStateFlow] and dispatches changes to
 * the registered processor for that node's type.
 *
 * The default implementation in `/shared` (`DefaultNodeObserver`) does
 * exactly that — it stays out of the SDK because it depends on the
 * `KrillApp.emit` sidecar, which performs Koin-driven processor lookups
 * the SDK has no visibility into. SDK consumers that want a custom
 * observer (e.g. a no-op for testing, or a record-and-replay implementation
 * for integration tests) implement this interface themselves.
 */
package krill.zone.shared.node

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Subscriber-and-dispatcher contract for the per-node state-flow streams.
 *
 * [NodeObserver] extends [AutoCloseable] so that callers can manage its
 * lifecycle with a `use {}` block or any other `AutoCloseable`-aware
 * resource-management mechanism. Implementations **must** cancel every
 * child coroutine inside [close]; failing to do so leaks coroutines beyond
 * the observer's intended lifetime.
 *
 * The canonical implementation pattern (as used by `DefaultNodeObserver`):
 *
 * ```kotlin
 * class MyObserver(parentScope: CoroutineScope) : NodeObserver {
 *     private val scope = CoroutineScope(
 *         parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
 *     )
 *     // ... observe/remove launch into scope ...
 *     override fun close() = scope.cancel()
 * }
 * ```
 */
interface NodeObserver : AutoCloseable {
    /**
     * Begins observing the given node's state flow. Implementations must
     * dispatch each new value to the appropriate processor and tolerate
     * the same id being observed twice (idempotent).
     */
    fun observe(node: MutableStateFlow<Node>)

    /** Stops observing the node with the given [id]. No-op if not observing. */
    fun remove(id: String)

    /**
     * Tears down all observation and cancels every child coroutine.
     *
     * Implementations **must** cancel all coroutines launched for observation
     * here. Omitting cancellation causes coroutines to outlive the observer,
     * leaking memory, processing work, and potentially driving disposed UI state.
     */
    override fun close()
}
