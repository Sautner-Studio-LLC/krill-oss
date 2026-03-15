package krill.zone.shared.node

import androidx.compose.runtime.*
import kotlinx.serialization.*
import krill.zone.shared.*

@Immutable
@Serializable
data class Node  (
    val id: String,
    val parent: String,
    val host: String,
    val type: KrillApp,
    val state: NodeState = NodeState.NONE,
    val meta: NodeMetaData,
    val timestamp: Long = 0L
)






