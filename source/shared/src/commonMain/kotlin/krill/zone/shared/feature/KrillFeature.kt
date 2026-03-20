package krill.zone.shared.feature


import kotlinx.serialization.*

@Serializable
data class KrillFeature(
    @SerialName("category")
    val category: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("llmActsOnExternalWorld")
    val llmActsOnExternalWorld: Boolean,
    @SerialName("llmBehavior")
    val llmBehavior: List<String>,
    @SerialName("llmCanCreateChildren")
    val llmCanCreateChildren: Boolean,
    @SerialName("llmConnectionHints")
    val llmConnectionHints: LlmConnectionHints,
    @SerialName("llmCreationHints")
    val llmCreationHints: String,
    @SerialName("llmExamples")
    val llmExamples: List<String>,
    @SerialName("llmExecutesChildren")
    val llmExecutesChildren: Boolean,
    @SerialName("llmInputs")
    val llmInputs: List<String>,
    @SerialName("llmOutputs")
    val llmOutputs: List<String>,
    @SerialName("llmPromptHints")
    val llmPromptHints: List<String>,
    @SerialName("llmPurpose")
    val llmPurpose: String,
    @SerialName("llmRepresentsPersistentState")
    val llmRepresentsPersistentState: Boolean,
    @SerialName("llmRole")
    val llmRole: String,
    @SerialName("llmSideEffectLevel")
    val llmSideEffectLevel: String,
    @SerialName("llmTargetType")
    val llmTargetType: String? = null,
    @SerialName("llmTypicalUseCases")
    val llmTypicalUseCases: List<String>,
    @SerialName("name")
    val name: String,
    @SerialName("nodeClickBehavior")
    val nodeClickBehavior: String,
    @SerialName("nodeCommandBehavior")
    val nodeCommandBehavior: String,
    @SerialName("shortDescription")
    val shortDescription: String,
    @SerialName("state")
    val state: String,
    @SerialName("subcategory")
    val subcategory: String,
    @SerialName("title")
    val title: String
)