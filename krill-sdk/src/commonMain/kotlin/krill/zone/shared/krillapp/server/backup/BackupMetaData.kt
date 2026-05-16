/**
 * Metadata for the `Server.Backup` node — captures and restores tar archives
 * of the server's persistent state (H2 database, project files, optional
 * camera thumbnails).
 *
 * The archive itself lives on the server's filesystem; this metadata only
 * stores the configuration of *what* to back up. The transient [action] and
 * [restoreFile] fields are used for one-shot per-execution input and are
 * intentionally not serialised onto the node.
 */
package krill.zone.shared.krillapp.server.backup

import kotlinx.serialization.*
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Server.Backup` node.
 */
@Serializable
data class BackupMetaData(
    /** Display name shown in the editor and on the node chip. */
    val name: String = "",
    /** Filesystem path on the server where archives are written / read. */
    val backupPath: String = "",
    /** When `true`, snapshot history is included in the archive. */
    val includeSnapshotData: Boolean = true,
    /** When `true`, project / diagram files are included in the archive. */
    val includeProjectData: Boolean = true,
    /** When `true`, saved camera thumbnails are included in the archive. */
    val includeCameraThumbnails: Boolean = true,
    /** Maximum age of archives to keep, in days. `0` disables retention pruning. */
    val maxAgeDays: Int = 0,
    /** Per-execution input: which direction (backup or restore) the next fire takes. Not serialised. */
    @Transient val action: BackupAction = BackupAction.BACKUP,
    /** Per-execution input: filename of archive to restore from. Not serialised. */
    @Transient val restoreFile: String = "",
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData
