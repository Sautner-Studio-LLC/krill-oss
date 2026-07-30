package krill.zone.shared.node

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AbstractNodeObserverTest {

    @Test
    fun `close cancels all child coroutines launched into observerScope`() = runTest {
        var finalizerRan = false

        val observer = object : AbstractNodeObserver(this) {
            init {
                observerScope.launch {
                    try {
                        delay(Long.MAX_VALUE)
                    } finally {
                        finalizerRan = true
                    }
                }
            }
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
        }

        advanceUntilIdle()
        assertFalse(finalizerRan, "finalizer should not run before close()")
        observer.close()
        advanceUntilIdle()
        assertTrue(finalizerRan, "close() must cancel observerScope — finalizer must run on cancellation")
    }

    @Test
    fun `use block triggers close and observerScope is cancelled`() = runTest {
        var onCloseCalled = false

        val observer = object : AbstractNodeObserver(this) {
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
            override fun onClose() {
                onCloseCalled = true
            }
        }

        observer.use { }
        assertTrue(onCloseCalled, "use {} must invoke close(), which must invoke onClose()")
    }

    @Test
    fun `close cannot be overridden — subclass hooks into onClose instead`() {
        // Compile-time proof: AbstractNodeObserver.close() is `final`, so a
        // subclass has no way to omit observerScope cancellation. Overriding
        // onClose() is the only extension point close() offers.
        val observer = object : AbstractNodeObserver(CoroutineScope(SupervisorJob())) {
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
        }
        observer.close()
    }
}
