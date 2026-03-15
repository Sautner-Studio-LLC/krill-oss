package krill.zone.server

import krill.zone.shared.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import krill.zone.shared.node.persistence.*
import org.koin.core.component.*
import java.net.*
import kotlin.uuid.*

object ServerIdentity : KoinComponent {
    const val DEFAULT_PORT = 8442

    private val nodePersistence: NodePersistence by inject()
    @OptIn(ExperimentalUuidApi::class)
    fun getSelfWithInfo(): Node {
        val stored = nodePersistence.read(installId())

        val host = stored
            ?: NodeBuilder()
                .type(KrillApp.Server)
                .id(installId())
                .parent(installId())
                .host(installId())
                .state(NodeState.NONE)
                .meta(KrillApp.Server.meta())
                .create()

        val meta = serverMetaData(host)


        return host.copy(meta = meta)

    }


    private fun resolveHostName(): String {
        fun isValid(name: String?) = !name.isNullOrBlank() && name != "localhost"

        // 1. Kernel hostname via /proc (most reliable on Linux, no process spawn)
        try {
            val procHostname = java.io.File("/proc/sys/kernel/hostname")
            if (procHostname.exists()) {
                val name = procHostname.readText().trim()
                if (isValid(name)) return name
            }
        } catch (_: Exception) {}

        // 2. Execute `hostname` command (reads kernel hostname)
        try {
            val process = ProcessBuilder("hostname").start()
            val name = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (isValid(name)) return name
        } catch (_: Exception) {}

        // 3. /etc/hostname file
        try {
            val hostnameFile = java.io.File("/etc/hostname")
            if (hostnameFile.exists()) {
                val name = hostnameFile.readText().trim()
                if (isValid(name)) return name
            }
        } catch (_: Exception) {}

        // 4. HOSTNAME env var
        val envHostname = System.getenv("HOSTNAME")
        if (isValid(envHostname)) return envHostname!!

        // 5. Last resort: InetAddress (unreliable on some Linux configs)
        return try {
            val name = InetAddress.getLocalHost().hostName
            if (isValid(name)) name else "unknown"

        } catch (_: Exception) { "unknown" }
    }

    private fun serverMetaData(node: Node): ServerMetaData {
        val meta = node.meta as ServerMetaData
        val hostName = resolveHostName()
        val name = meta.name.ifEmpty { hostName }
        val port = if (meta.port == 0) { DEFAULT_PORT } else { meta.port }
        val host =  meta.copy(
            name = name,
            platform = platform,
            port = port,
        )
        return host

    }
}