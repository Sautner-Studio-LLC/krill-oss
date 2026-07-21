package krill.zone.shared.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Scope-owning base for [NodeObserver] implementations.
 *
 * Owns a [observerScope] that is a supervised child of [parentScope].
 * Subclasses launch every observation coroutine into [observerScope];
 * [close] cancels it, propagating cancellation to all children without
 * disturbing the parent.
 *
 * [close] is `final` so [observerScope] cancellation is guaranteed at the
 * type level rather than by convention. Subclasses that need additional
 * teardown override [onClose] instead.
 */
abstract class AbstractNodeObserver(parentScope: CoroutineScope) : NodeObserver {

    protected val observerScope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )

    final override fun close() {
        observerScope.cancel()
        onClose()
    }

    /** Hook for subclass-specific teardown. Runs after [observerScope] is cancelled. */
    protected open fun onClose() {}
}
