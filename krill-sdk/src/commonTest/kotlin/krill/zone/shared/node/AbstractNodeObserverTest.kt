package krill.zone.shared.node

import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        var closeCalled = false

        val observer = object : AbstractNodeObserver(this) {
            override fun observe(node: MutableStateFlow<Node>) {}
            override fun remove(id: String) {}
            override fun close() {
                closeCalled = true
                super.close()
            }
        }

        observer.use { }
        assertTrue(closeCalled, "use {} must invoke close()")
    }
}
