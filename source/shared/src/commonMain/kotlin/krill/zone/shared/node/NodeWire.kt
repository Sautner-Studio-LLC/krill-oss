package krill.zone.shared.node

import androidx.compose.runtime.*
import kotlinx.serialization.*
import krill.zone.shared.*

@Serializable @Immutable
data class NodeWire(
    val timestamp: Long,
    val installId: String,
    private val host: String,
    val port: Int,
    val platform: Platform,
    val clusterToken: String = ""
)  {
    fun host() : String {
        return host.replace("\n","").trim().removeSuffix("http://").removeSuffix("https://")
    }
}



fun NodeWire.url() : String {
    return "https://${host()}:$port"
}



