package krill.zone.shared.feature


import kotlinx.serialization.*

@Serializable
data class LlmConnectionHints(
    @SerialName("childTypes")
    val childTypes: List<String> = emptyList(),
    @SerialName("parentTypes")
    val parentTypes: List<String> = emptyList(),
    @SerialName("sourceTypes")
    val sourceTypes: List<String> = emptyList(),
    @SerialName("targetTypes")
    val targetTypes: List<String> = emptyList(),
    @SerialName("role")
    val role: String
)