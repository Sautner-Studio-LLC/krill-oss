package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.json.Json
import krill.zone.shared.krillapp.datapoint.FileRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for krill-oss#217: the `SwarmPlan` response schema.
 *
 * Covers:
 *  - An empty `subtasks` list ("do it myself") is a valid, round-trippable plan.
 *  - A populated plan with subtasks round-trips through JSON.
 */
class SwarmPlanTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an empty subtasks list is a valid do-it-myself plan`() {
        val plan = SwarmPlan(subtasks = emptyList(), assembly = Assembly.CONCAT, rationale = "small enough to do directly")
        assertTrue(plan.subtasks.isEmpty())

        val encoded = json.encodeToString(SwarmPlan.serializer(), plan)
        val decoded = json.decodeFromString(SwarmPlan.serializer(), encoded)
        assertEquals(plan, decoded)
    }

    @Test
    fun `a populated SwarmPlan with subtasks round-trips through JSON`() {
        val plan = SwarmPlan(
            subtasks = listOf(
                SubTask(prompt = "part 1", requiredModel = "llava", minVramClass = "24g", decomposable = false),
                SubTask(
                    prompt = "part 2",
                    fileRefs = listOf(FileRef(hash = "h1", sizeBytes = 5, mime = "text/plain", host = "srv-1")),
                    decomposable = true,
                ),
            ),
            assembly = Assembly.SYNTHESIZE,
            rationale = "split by input frame",
        )
        val encoded = json.encodeToString(SwarmPlan.serializer(), plan)
        val decoded = json.decodeFromString(SwarmPlan.serializer(), encoded)
        assertEquals(plan, decoded)
    }

    @Test
    fun `SubTask fields default correctly`() {
        val subtask = SubTask(prompt = "just the prompt")
        assertEquals(emptyList(), subtask.fileRefs)
        assertEquals("", subtask.requiredModel)
        assertEquals("", subtask.minVramClass)
        assertEquals(false, subtask.decomposable)
    }
}
