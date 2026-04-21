package krill.zone.server.events

import krill.zone.shared.node.NodeState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class SseStateForwardingTest {

    @Test
    fun `every NodeState is either forwarded or filtered`() {
        val classified = SseStateForwarding.forwarded + SseStateForwarding.filtered
        val unclassified = NodeState.entries.filter { it !in classified }
        assertTrue(
            unclassified.isEmpty(),
            "NodeState values missing from SseStateForwarding: $unclassified " +
                "(add to SseStateForwarding.forwarded or SseStateForwarding.filtered)"
        )
    }

    @Test
    fun `forwarded and filtered sets are disjoint`() {
        val overlap = SseStateForwarding.forwarded intersect SseStateForwarding.filtered
        assertTrue(overlap.isEmpty(), "state appears in both sets: $overlap")
    }

    @Test
    fun `PROCESSING is forwarded`() {
        assertTrue(SseStateForwarding.shouldForward(NodeState.PROCESSING))
    }

    @Test
    fun `NONE is filtered`() {
        assertFalse(SseStateForwarding.shouldForward(NodeState.NONE))
    }

    @Test
    fun `classification covers exactly the enum universe`() {
        val classified = SseStateForwarding.forwarded + SseStateForwarding.filtered
        assertEquals(NodeState.entries.toSet(), classified)
    }
}
