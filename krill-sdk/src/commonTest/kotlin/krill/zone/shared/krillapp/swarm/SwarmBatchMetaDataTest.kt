package krill.zone.shared.krillapp.swarm

import kotlinx.serialization.json.Json
import krill.zone.shared.krillapp.datapoint.FileRef
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for krill-oss#217: `SwarmBatch` metadata.
 *
 * Covers:
 *  - Every field defaults to a safe, additive value.
 *  - An empty pre-existing payload deserializes with those defaults intact.
 *  - A populated batch (items + shared requirements + progress counts)
 *    round-trips through JSON.
 */
class SwarmBatchMetaDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `SwarmBatchMetaData defaults are safe and additive`() {
        val meta = SwarmBatchMetaData()
        assertEquals(emptyList(), meta.items)
        assertEquals("", meta.requiredModel)
        assertEquals("", meta.minVramClass)
        assertEquals(Double.MAX_VALUE, meta.maxCostScore)
        assertEquals(0L, meta.deadlineAt)
        assertEquals(SwarmBatchMetaData.DEFAULT_MAX_CHILDREN, meta.maxChildren)
        assertEquals(0, meta.completedCount)
        assertEquals(0, meta.failedCount)
    }

    @Test
    fun `an empty pre-existing payload deserializes with safe defaults`() {
        val meta = json.decodeFromString(SwarmBatchMetaData.serializer(), "{}")
        assertEquals(emptyList(), meta.items)
        assertEquals(SwarmBatchMetaData.DEFAULT_MAX_CHILDREN, meta.maxChildren)
    }

    @Test
    fun `a populated SwarmBatchMetaData round-trips through JSON`() {
        val original = SwarmBatchMetaData(
            items = listOf(
                SwarmBatchItem(prompt = "item 1"),
                SwarmBatchItem(
                    prompt = "item 2",
                    images = listOf("frame"),
                    fileRefs = listOf(FileRef(hash = "h2", sizeBytes = 20, mime = "text/plain", host = "srv-2")),
                ),
            ),
            requiredModel = "qwen2.5",
            minVramClass = "8g",
            maxCostScore = 3.0,
            deadlineAt = 555L,
            maxChildren = 4,
            completedCount = 1,
            failedCount = 0,
        )
        val encoded = json.encodeToString(SwarmBatchMetaData.serializer(), original)
        val decoded = json.decodeFromString(SwarmBatchMetaData.serializer(), encoded)
        assertEquals(original, decoded)
    }
}
