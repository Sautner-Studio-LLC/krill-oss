/**
 * Direction selector for the `Server.Backup` node — distinguishes a "take a
 * backup" execution from a "restore from backup" execution. Used as a
 * `@Transient` field on [BackupMetaData] so the action choice is per-fire,
 * not persisted on the node.
 */
package krill.zone.shared.krillapp.server.backup

import kotlinx.serialization.*

/** What the `Server.Backup` node should do when executed. */
@Serializable
enum class BackupAction {
    /** Capture a fresh archive of the configured data sources. */
    BACKUP,

    /** Restore data from a previously captured archive. */
    RESTORE,
}
