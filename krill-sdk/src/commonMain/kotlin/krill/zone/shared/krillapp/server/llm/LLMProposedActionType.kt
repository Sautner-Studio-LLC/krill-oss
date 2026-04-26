/**
 * Discriminator for the kind of action an LLM agent is proposing inside an
 * [LLMProposedAction]. Each value selects which fields of the proposal are
 * meaningful (e.g. `CREATE_NODES` populates `newNodes`; `CREATE_LINKS`
 * populates `newLinks`).
 *
 * `@SerialName` set to snake_case for parity with the upstream agent's JSON.
 */
package krill.zone.shared.krillapp.server.llm

import kotlinx.serialization.*

/**
 * The category of action an LLM agent can propose.
 */
@Serializable
enum class LLMProposedActionType {
    /** Create one or more new nodes. */
    @SerialName("create_nodes")
    CREATE_NODES,

    /** Create source / target links between existing or proposed nodes. */
    @SerialName("create_links")
    CREATE_LINKS,

    /** Update metadata or state on an existing node. */
    @SerialName("update_node")
    UPDATE_NODE,

    /** Surface an explanation of a node or subgraph rather than mutate state. */
    @SerialName("explain")
    EXPLAIN,
}
