package krill.zone.shared.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for krill-oss#176: NodeObserver must be AutoCloseable so
 * callers can manage its lifecycle with use {} and so that the close() contract
 * (cancel all child coroutines) is type-enforced.
 */
class NodeObserverLifecycleTest {

    @Test
    fun `NodeObserver is AutoCloseable — can be assigned to AutoCloseable reference`() {
        val observer: AutoCloseable = object : NodeObserver {
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
            override fun close() {}
        }
        observer.close()
    }

    @Test
    fun `use {} block invokes close on NodeObserver`() {
        var closeCalled = false
        val observer = object : NodeObserver {
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
            override fun close() { closeCalled = true }
        }
        observer.use { }
        assertTrue(closeCalled, "use {} must have called close()")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `scope-bound implementation cancels child coroutines when close is called`() = runTest {
        // Minimal scope-bound NodeObserver that mirrors the DefaultNodeObserver pattern.
        val parentJob = coroutineContext[Job]
        val childScope = CoroutineScope(coroutineContext + SupervisorJob(parentJob))
        var finalizerRan = false

        val observer = object : NodeObserver {
            init {
                childScope.launch {
                    try {
                        delay(Long.MAX_VALUE)
                    } finally {
                        finalizerRan = true
                    }
                }
            }
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
            override fun close() = childScope.cancel()
        }

        // Let the launched coroutine start and suspend at delay() before cancelling.
        // Without this, cancel() hits the coroutine before it runs its body, so
        // the try-finally never executes and the assertion below would be vacuous.
        advanceUntilIdle()
        assertFalse(finalizerRan, "finalizer should not have run before close()")
        observer.close()
        advanceUntilIdle()
        assertTrue(finalizerRan, "close() must cancel child coroutines — finalizer should run on cancellation")
    }
}
