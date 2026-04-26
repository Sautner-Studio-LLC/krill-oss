/**
 * Slim form of [krill.zone.shared.feature.KrillFeature] sent to the LLM
 * agent for each unique node type in the prompt context. Strips the JSON
 * bundle's UI-only fields (display name, palette colour, click behaviour)
 * to keep the agent's context window focused on what matters for planning:
 * purpose, behaviour, I/O signature, and connection rules.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Compact feature contract as sent to the agent.
 *
 * Field names mirror their counterparts on [krill.zone.shared.feature.KrillFeature]
 * so a human reading the agent's prompt gets a familiar shape.
 */
@Serializable
data class LLMFeatureSummary(
    val name: String,
    val title: String,
    val shortDescription: String,
    val llmPurpose: String,
    val llmRole: String,
    val llmBehavior: List<String>,
    val llmInputs: List<String>,
    val llmOutputs: List<String>,
    val llmCreationHints: String,
    val llmConnectionHints: LLMConnectionHintsSummary,
    val llmSideEffectLevel: String,
    val llmCanCreateChildren: Boolean,
    val llmActsOnExternalWorld: Boolean,
    val llmExamples: List<String>,
)
