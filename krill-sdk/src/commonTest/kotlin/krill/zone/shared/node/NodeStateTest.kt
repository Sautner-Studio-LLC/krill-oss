package krill.zone.shared.node

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression for krill-oss#158: NodeState.isInvokable() codifies which states
 * allow a deliberate invocation, preventing client-side fires against PAUSED
 * or DELETING nodes.
 */
class NodeStateTest {

    @Test
    fun `PAUSED is not invokable — processor is deliberately suspended`() {
        assertFalse(NodeState.PAUSED.isInvokable())
    }

    @Test
    fun `DELETING is not invokable — node is being removed`() {
        assertFalse(NodeState.DELETING.isInvokable())
    }

    @Test
    fun `NONE is invokable — idle default state`() {
        assertTrue(NodeState.NONE.isInvokable())
    }

    @Test
    fun `INFO WARN SEVERE ERROR are invokable — degraded but still running`() {
        assertTrue(NodeState.INFO.isInvokable())
        assertTrue(NodeState.WARN.isInvokable())
        assertTrue(NodeState.SEVERE.isInvokable())
        assertTrue(NodeState.ERROR.isInvokable())
    }

    @Test
    fun `PROCESSING and EXECUTED are invokable — active execution states`() {
        assertTrue(NodeState.PROCESSING.isInvokable())
        assertTrue(NodeState.EXECUTED.isInvokable())
    }

    @Test
    fun `RESET is invokable — completed reset node is stable`() {
        assertTrue(NodeState.RESET.isInvokable())
    }

    @Test
    fun `all non-blocked states are invokable`() {
        val blocked = setOf(NodeState.PAUSED, NodeState.DELETING)
        for (state in NodeState.entries) {
            if (state in blocked) continue
            assertTrue(state.isInvokable(), "Expected $state to be invokable")
        }
    }
}
