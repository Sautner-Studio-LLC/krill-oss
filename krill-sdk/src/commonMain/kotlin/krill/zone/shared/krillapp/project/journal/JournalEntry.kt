/**
 * A single user-authored entry inside a `Project.Journal` node. Lives inside
 * [JournalMetaData.entries]; the journal node owns the list and is the
 * single source of truth for ordering and timestamps.
 */
package krill.zone.shared.krillapp.project.journal

import kotlinx.serialization.*

/**
 * One journal entry — text body plus optional photos.
 *
 * `id` is generated client-side at creation time so the editor can reorder
 * entries optimistically without round-tripping to the server.
 */
@Serializable
data class JournalEntry(
    /** Stable per-entry UUID. */
    val id: String = "",
    /** Markdown-flavoured text body. */
    val content: String = "",
    /** URLs of photos uploaded for this entry, in the order the user added them. */
    val photoUrls: List<String> = emptyList(),
    /** Epoch millis the entry was created. `0` for entries that haven't been saved yet. */
    val createdAt: Long = 0L,
    /** Epoch millis of the most recent edit. */
    val updatedAt: Long = 0L,
)
