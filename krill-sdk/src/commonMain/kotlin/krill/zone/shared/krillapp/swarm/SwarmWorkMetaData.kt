/**
 * Metadata for a `Swarm.Work` node — a single unit of LLM work advertised to
 * the swarm for a remote node to claim and execute.
 *
 * SDK-only contract: this class carries no dispatch/arbitration behavior —
 * that lives in the krill server processor that pins this release. Everything
 * here is additive with back-compat defaults so pre-existing payloads (there
 * are none yet, but future fields will follow the same rule) round-trip.
 */
package krill.zone.shared.krillapp.swarm

import kotlinx.serialization.*
import krill.zone.shared.krillapp.datapoint.FileRef
import krill.zone.shared.krillapp.datapoint.Snapshot
import krill.zone.shared.node.InvocationTrigger
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.SourceMetaData

/**
 * Lifecycle phase of a [SwarmWorkMetaData] node, from advertised to settled.
 *
 * Wire form is the enum name — do not reorder or rename without a coordinated
 * migration.
 */
@Serializable
enum class SwarmWorkPhase {
    /** Advertised to the swarm; no node has claimed it yet. */
    OPEN,

    /** A remote node's [krill.zone.shared.node.NodeAction.CLAIM] has been accepted. */
    ASSIGNED,

    /** The assignee is actively executing the work. */
    RUNNING,

    /** Work completed successfully; [SwarmWorkMetaData.result] holds the answer. */
    DONE,

    /** Work could not be completed; see [SourceMetaData.error]. */
    FAILED,
}

/**
 * Payload for a `Swarm.Work` node.
 */
@Serializable
data class SwarmWorkMetaData(
    /** The task prompt sent to whichever node claims this work. */
    val prompt: String,
    /** Small inline images (camera-frame scale base64), rides with the prompt. */
    val images: List<String> = emptyList(),
    /** Claim-check refs to larger files; pulled lazily by the winner. */
    val fileRefs: List<FileRef> = emptyList(),
    /** Required advertised model name; empty means any advertised model is eligible. */
    val requiredModel: String = "",
    /** Minimum advertised VRAM class an eligible claimant must report. */
    val minVramClass: String = "",
    /** Maximum advertised cost score an eligible claimant may report. */
    val maxCostScore: Double = Double.MAX_VALUE,
    /** Maximum payload size (prompt + images, in bytes) a claimant will accept. */
    val maxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD,
    /** Epoch millis after which this work is abandoned; `0` means wait forever. */
    val deadlineAt: Long = 0,
    /** How long an assignee's claim is held before it is considered abandoned. */
    val leaseTtlMs: Long = DEFAULT_LEASE_TTL,
    /** How long this work stays open for bids before the best claim is accepted. */
    val claimWindowMs: Long = DEFAULT_CLAIM_WINDOW,
    /** `true` if this work is eligible to be split into subtasks (foreman pattern). */
    val decomposable: Boolean = false,
    /** Remaining recursion budget for further decomposition. */
    val delegationDepth: Int = 0,
    /** Maximum number of subtasks a decomposition may produce. */
    val maxSubtasks: Int = DEFAULT_MAX_SUBTASKS,
    /** The node that currently holds the claim, or `null` while [phase] is [SwarmWorkPhase.OPEN]. */
    val assignee: NodeIdentity? = null,
    /** Number of execution attempts made so far. */
    val attempts: Int = 0,
    /** Maximum execution attempts before this work is marked [SwarmWorkPhase.FAILED]. */
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    /** The work's result — text, or a serialized [FileRef], once [phase] reaches [SwarmWorkPhase.DONE]. */
    val result: Snapshot = Snapshot(),
    /** Current lifecycle phase. */
    val phase: SwarmWorkPhase = SwarmWorkPhase.OPEN,

    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val snapshot: Snapshot = Snapshot(),
    override val invocationTriggers: List<InvocationTrigger> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
    override val inputs: List<NodeIdentity> = emptyList(),
) : SourceMetaData {
    override fun withError(error: String) = copy(error = error)
    override fun displayName() = ""

    companion object {
        /** Default cap on prompt + images payload size, in bytes (1 MiB). */
        const val DEFAULT_MAX_PAYLOAD: Int = 1_048_576
        /** Default lease hold time for an accepted claim, in milliseconds (30s). */
        const val DEFAULT_LEASE_TTL: Long = 30_000L
        /** Default bidding window before the best claim is accepted, in milliseconds (5s). */
        const val DEFAULT_CLAIM_WINDOW: Long = 5_000L
        /** Default cap on subtasks produced by one decomposition. */
        const val DEFAULT_MAX_SUBTASKS: Int = 8
        /** Default cap on execution attempts before giving up. */
        const val DEFAULT_MAX_ATTEMPTS: Int = 3
    }
}
