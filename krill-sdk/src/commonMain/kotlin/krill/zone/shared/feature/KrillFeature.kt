/**
 * Static descriptor for a single Krill node type. One [KrillFeature] is
 * shipped per `KrillApp` subclass as a JSON resource (e.g.
 * `KrillApp.Server.json`) and loaded at runtime by `FeatureLoader` to power
 * both the in-app palette and the LLM-driven node creation flow.
 *
 * Most fields are LLM-oriented prompts and hints: they are read by the model
 * when it needs to reason about whether to suggest, create, or wire a given
 * node type.
 *
 * The shape is deliberately string-heavy (rather than typed enums) so the JSON
 * bundles can evolve faster than the SDK schema.
 */
package krill.zone.shared.feature

import kotlinx.serialization.*

/**
 * Descriptor for a single Krill node type — UI metadata plus the prompt
 * surface used by Krill's LLM tooling.
 *
 * Loaded from a JSON resource named after the corresponding `KrillApp` subclass
 * (e.g. `KrillApp.Server.json`). A missing field on the JSON side falls back
 * to the default declared here; required fields will throw at deserialisation
 * time, which is the desired fail-loud behaviour for a malformed bundle.
 */
@Serializable
data class KrillFeature(
    /** Top-level grouping in the palette (`"Trigger"`, `"Executor"`, ...). */
    @SerialName("category")
    val category: String = "",

    /** Long-form prose description shown in the editor's info panel. */
    @SerialName("description")
    val description: String = "",

    /**
     * `true` if this node type has side effects outside the swarm (sends
     * email, calls an external API, toggles a relay) — used to prompt the
     * user for confirmation before LLM-driven creation.
     */
    @SerialName("llmActsOnExternalWorld")
    val llmActsOnExternalWorld: Boolean,

    /** Bullet-list description of how this node behaves at runtime. */
    @SerialName("llmBehavior")
    val llmBehavior: List<String>,

    /** `true` if the LLM is allowed to add child nodes underneath this type. */
    @SerialName("llmCanCreateChildren")
    val llmCanCreateChildren: Boolean,

    /** Connection-graph hints; see [LlmConnectionHints]. */
    @SerialName("llmConnectionHints")
    val llmConnectionHints: LlmConnectionHints,

    /** Free-form guidance for the LLM when it decides to instantiate this type. */
    @SerialName("llmCreationHints")
    val llmCreationHints: String,

    /** Worked examples shown to the LLM as few-shot context. */
    @SerialName("llmExamples")
    val llmExamples: List<String>,

    /** `true` if this node type executes its children (e.g. a Project orchestrating its members). */
    @SerialName("llmExecutesChildren")
    val llmExecutesChildren: Boolean,

    /** Names of the values this node consumes from upstream sources. */
    @SerialName("llmInputs")
    val llmInputs: List<String>,

    /** Names of the values this node produces for downstream targets. */
    @SerialName("llmOutputs")
    val llmOutputs: List<String>,

    /** Phrases the LLM should look for in user requests as a hint to use this node. */
    @SerialName("llmPromptHints")
    val llmPromptHints: List<String>,

    /** One-sentence summary of what this node type is for. */
    @SerialName("llmPurpose")
    val llmPurpose: String,

    /** `true` if instances of this type carry persistent state between executions (e.g. a DataPoint's snapshot history). */
    @SerialName("llmRepresentsPersistentState")
    val llmRepresentsPersistentState: Boolean,

    /** Coarse role label — `"sensor"`, `"actuator"`, `"orchestrator"`, etc. */
    @SerialName("llmRole")
    val llmRole: String,

    /**
     * Coarse risk classification of running this node — typically one of
     * `"none"`, `"reversible"`, `"observable"`, `"irreversible"`. Used by the
     * LLM safety layer to decide whether to ask before firing.
     */
    @SerialName("llmSideEffectLevel")
    val llmSideEffectLevel: String,

    /**
     * For executor / filter / trigger types, the canonical node type they
     * typically write to. Optional and may be `null` for nodes that don't
     * have a single dominant target.
     */
    @SerialName("llmTargetType")
    val llmTargetType: String? = null,

    /** Catalogue of typical use cases shown alongside [llmExamples]. */
    @SerialName("llmTypicalUseCases")
    val llmTypicalUseCases: List<String>,

    /** Display name for the palette and editor. */
    @SerialName("name")
    val name: String,

    /**
     * What happens when the user clicks the node chip in the swarm UI —
     * `"none"`, `"execute"`, `"toggle"`, etc.
     */
    @SerialName("nodeClickBehavior")
    val nodeClickBehavior: String,

    /** Description of what the node's right-click / command menu offers. */
    @SerialName("nodeCommandBehavior")
    val nodeCommandBehavior: String,

    /**
     * `true` if this node type depends on a Krill server runtime
     * (DataPoint, Trigger, Executor, MQTT, Server.* …) — `false` for
     * fully client-side types (Project, Project.Journal, Project.TaskList,
     * Project.Diagram). Drives recipe gating and FTUE prompts.
     */
    @SerialName("requiresServer")
    val requiresServer: Boolean,

    /** Short description used in the node palette tile. */
    @SerialName("shortDescription")
    val shortDescription: String,

    /** Default initial [krill.zone.shared.node.NodeState] for newly created instances. */
    @SerialName("state")
    val state: String,

    /** Optional sub-grouping under [category] in the palette. */
    @SerialName("subcategory")
    val subcategory: String,

    /** Marketing / docs title — usually the same as [name] but may be longer. */
    @SerialName("title")
    val title: String,
)
