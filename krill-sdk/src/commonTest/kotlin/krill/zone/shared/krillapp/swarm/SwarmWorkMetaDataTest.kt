package krill.zone.shared.krillapp.swarm

import kotlinx.serialization.json.Json
import krill.zone.shared.krillapp.datapoint.FileRef
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Regression tests for krill-oss#217: `SwarmWork` metadata.
 *
 * Covers:
 *  - Every field defaults to a safe, additive value.
 *  - A pre-existing minimal payload (just the required `prompt`) deserializes
 *    with those defaults intact.
 *  - A fully populated instance round-trips through JSON.
 */
class SwarmWorkMetaDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SwarmWorkMetaData defaults are safe and additive`() {
        val meta = WorkMetaData(prompt = "summarise the last hour")
        assertEquals(emptyList(), meta.images)
        assertEquals(emptyList(), meta.fileRefs)
        assertEquals("", meta.requiredModel)
        assertEquals("", meta.minVramClass)
        assertEquals(Double.MAX_VALUE, meta.maxCostScore)
        assertEquals(WorkMetaData.DEFAULT_MAX_PAYLOAD, meta.maxPayloadBytes)
        assertEquals(0L, meta.deadlineAt)
        assertEquals(WorkMetaData.DEFAULT_LEASE_TTL, meta.leaseTtlMs)
        assertEquals(WorkMetaData.DEFAULT_CLAIM_WINDOW, meta.claimWindowMs)
        assertEquals(false, meta.decomposable)
        assertEquals(0, meta.delegationDepth)
        assertEquals(WorkMetaData.DEFAULT_MAX_SUBTASKS, meta.maxSubtasks)
        assertNull(meta.assignee)
        assertEquals(0, meta.attempts)
        assertEquals(WorkMetaData.DEFAULT_MAX_ATTEMPTS, meta.maxAttempts)
        assertEquals(Snapshot(), meta.result)
        assertEquals(SwarmWorkPhase.OPEN, meta.phase)
        assertEquals(NodeAction.EXECUTE, meta.nodeAction)
    }

    @Test
    fun `a minimal pre-existing payload deserializes with safe defaults`() {
        val payload = """{"prompt":"classify the image"}"""
        val meta = json.decodeFromString(WorkMetaData.serializer(), payload)

        assertEquals("classify the image", meta.prompt)
        assertEquals(SwarmWorkPhase.OPEN, meta.phase)
        assertNull(meta.assignee)
        assertEquals(0, meta.attempts)
    }

    @Test
    fun `a fully populated SwarmWorkMetaData round-trips through JSON`() {
        val original = WorkMetaData(
            prompt = "classify this frame",
            images = listOf("base64frame"),
            fileRefs = listOf(FileRef(hash = "h1", sizeBytes = 10, mime = "image/png", host = "srv-1")),
            requiredModel = "llava",
            minVramClass = "24g",
            maxCostScore = 5.0,
            maxPayloadBytes = 2048,
            deadlineAt = 123456L,
            leaseTtlMs = 60_000L,
            claimWindowMs = 10_000L,
            decomposable = true,
            delegationDepth = 2,
            maxSubtasks = 4,
            assignee = NodeIdentity(nodeId = "n1", hostId = "h1"),
            attempts = 1,
            maxAttempts = 5,
            result = Snapshot(timestamp = 999L, value = "done"),
            phase = SwarmWorkPhase.RUNNING,
            nodeAction = NodeAction.CLAIM,
        )
        val encoded = json.encodeToString(WorkMetaData.serializer(), original)
        val decoded = json.decodeFromString(WorkMetaData.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `SwarmWorkPhase covers the full advertised lifecycle`() {
        assertEquals(
            listOf(SwarmWorkPhase.OPEN, SwarmWorkPhase.ASSIGNED, SwarmWorkPhase.RUNNING, SwarmWorkPhase.DONE, SwarmWorkPhase.FAILED),
            SwarmWorkPhase.entries,
        )
    }
}
