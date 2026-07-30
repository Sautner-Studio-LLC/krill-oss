package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.FileRef

/**
 * How a [SwarmPlan]'s [SwarmPlan.subtasks] results are combined back into one
 * answer once every subtask completes.
 */
@Serializable
enum class Assembly {
    /** Concatenate each subtask's result in order — no further model call. */
    CONCAT,

    /** Feed every subtask's result back into the model to synthesize one answer. */
    SYNTHESIZE,
}

/**
 * One piece of a decomposed [SwarmPlan] — dispatched to the swarm as its own
 * `Swarm.Work` node.
 */
@Serializable
data class SubTask(
    val prompt: String,
    val fileRefs: List<FileRef> = emptyList(),
    val requiredModel: String = "",
    val minVramClass: String = "",
    val decomposable: Boolean = false,
)

/**
 * Response schema for an LLM node asked to plan how a `Swarm.Work` should be
 * decomposed (the foreman pattern).
 *
 * An empty [subtasks] list is itself a valid plan — "do it myself" — and
 * callers should treat that as the signal to execute the original work
 * directly rather than fanning out.
 */
@Serializable
data class SwarmPlan(
    val subtasks: List<SubTask>,
    val assembly: Assembly,
    /** Model's self-reported reasoning for this decomposition, empty when not needed. */
    val rationale: String = "",
)
