package krill.zone.shared.node

import kotlinx.coroutines.test.runTest
import krill.zone.shared.KrillApp
import krill.zone.shared.krillapp.server.pin.PinMetaData
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests for the [ServerNodeProcessor] / [ClientNodeProcessor] split
 * introduced by `separate-crud-from-invocation` Part 1.
 *
 * Covers:
 *  - [ServerNodeProcessor.onInvoke] default: EXECUTE delegates to [ServerNodeProcessor.process]
 *  - [ServerNodeProcessor.onInvoke] default: RESET is a no-op (does not call process)
 *  - [ClientNodeProcessor.post] is callable (compile-time shape check)
 *  - Back-compat: no payload field changes in this part; existing deserialization
 *    tests in [SourceVerbWiringTest] remain green.
 */
class NodeProcessorContractTest {

    private val testNode = Node(
        id = "n1",
        parent = "n1",
        host = "host-a",
        type = KrillApp.Server.Pin,
        meta = PinMetaData(name = "relay"),
    )

    @Test
    fun `ServerNodeProcessor default onInvoke delegates to process on EXECUTE`() = runTest {
        var processCallCount = 0
        val processor = object : ServerNodeProcessor {
            override suspend fun process(node: Node): Boolean {
                processCallCount++
                return true
            }
        }
        processor.onInvoke(testNode, NodeIdentity("btn-1", "host-a"), NodeAction.EXECUTE)
        assertEquals(1, processCallCount)
    }

    @Test
    fun `ServerNodeProcessor default onInvoke is no-op on RESET`() = runTest {
        var processCallCount = 0
        val processor = object : ServerNodeProcessor {
            override suspend fun process(node: Node): Boolean {
                processCallCount++
                return true
            }
        }
        processor.onInvoke(testNode, NodeIdentity("btn-1", "host-a"), NodeAction.RESET)
        assertEquals(0, processCallCount, "RESET must not call process — it is terminal at the receiver")
    }

    @Test
    fun `ServerNodeProcessor by parameter carries full NodeIdentity`() = runTest {
        var capturedBy: NodeIdentity? = null
        val processor = object : ServerNodeProcessor {
            override suspend fun onInvoke(node: Node, by: NodeIdentity, verb: NodeAction) {
                capturedBy = by
            }
            override suspend fun process(node: Node): Boolean = true
        }
        val identity = NodeIdentity(nodeId = "btn-42", hostId = "host-b")
        processor.onInvoke(testNode, identity, NodeAction.EXECUTE)
        assertEquals(identity, capturedBy)
        assertEquals("host-b", capturedBy!!.hostId, "hostId must survive the call — never collapsed to a bare nodeId")
    }

    @Test
    fun `ClientNodeProcessor post is a non-suspend dispatch method`() {
        var postCallCount = 0
        val processor = object : ClientNodeProcessor {
            override fun post(node: Node) {
                postCallCount++
            }
        }
        processor.post(testNode)
        assertEquals(1, postCallCount)
    }
}
