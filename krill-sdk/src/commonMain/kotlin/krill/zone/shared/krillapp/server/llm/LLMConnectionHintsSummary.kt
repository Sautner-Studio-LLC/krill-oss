/**
 * Slim form of [krill.zone.shared.feature.LlmConnectionHints] sent to the
 * agent inside a [LLMFeatureSummary]. Only carries the four "neighbour
 * type" lists plus the role string — no other [krill.zone.shared.feature.KrillFeature]
 * metadata — so the prompt context stays small.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * Connection-graph hints in their compact agent-context form.
 *
 * Each list contains canonical KrillApp short-name strings (e.g.
 * `"DataPoint.Filter.Deadband"`).
 */
@Serializable
data class LLMConnectionHintsSummary(
    /** Types that typically own this one as a child. */
    val parentTypes: List<String> = emptyList(),
    /** Types that typically appear as direct children of this one. */
    val childTypes: List<String> = emptyList(),
    /** Types that typically feed values into this one. */
    val sourceTypes: List<String> = emptyList(),
    /** Types this one typically writes to. */
    val targetTypes: List<String> = emptyList(),
    /** Free-form role label — `"sensor"`, `"actuator"`, etc. */
    val role: String = "",
)
