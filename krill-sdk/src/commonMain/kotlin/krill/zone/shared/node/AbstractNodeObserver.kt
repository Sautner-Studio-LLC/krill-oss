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
 * Overriding [close] is allowed, but **must** call `super.close()`.
 */
abstract class AbstractNodeObserver(parentScope: CoroutineScope) : NodeObserver {

    protected val observerScope: CoroutineScope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )

    override fun close() {
        observerScope.cancel()
    }
}
