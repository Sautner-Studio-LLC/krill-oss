package krill.zone.server.io.beacon

import androidx.compose.runtime.*
import co.touchlab.kermit.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import org.koin.ext.*
import kotlin.uuid.*


/**
 * Source of starting a connection:
 * * Peer Beacon (cluster membership already validated by rolling token)
 * * User Added with form
 * * App / Server Startup with stored server nodes
 *
 * With PIN-based auth, all cluster members share the same Bearer token derived
 * from the PIN. No per-peer key exchange (handshake) is needed.
 */
class ServerServerConnector(

    private val nodeManager: ServerNodeManager,
    private val nodeHttp: NodeHttp,
    private val scope: CoroutineScope
) : ServerConnector {

    private val logger = Logger.withTag(this::class.getFullName())
    private val mutex = Mutex()
    private val jobs = mutableMapOf<String, Job>()

    /**
     * Entry point for connecting to a server with a beacon
     */
    override suspend fun connectWire(wire: NodeWire) {
        connectNode(convertWireToServer(wire))
    }

    /**
     * Entry point for connecting to an external server with no beacon
     */
    override suspend fun connectMeta(meta: ServerMetaData) {
        connectNode(convertMetaToServer(meta))
    }


    /**
     * Entry point for connecting
     */
    override suspend fun connectNode(node: Node) {
        if (!performConnectionSanityCheck(node)) {
            return
        }
        beginConnectionAttempt(node)
    }

    private suspend fun beginConnectionAttempt(node: Node) {
        mutex.withLock {
            val job = scope.launch {
                try {
                    val n = mutableStateOf(node)
                    logger.d { "${n.value.details()}: starting connection" }
                    createNodeRecordIfMissing(n.value)

                    downloadServerCert(n.value)

                    if (n.value.id == TEMP_ID_FOR_CONNECTING) {
                        n.value = downloadServer(n.value) ?: throw Exception("${n.value.details()}: Server not connected")
                        createNodeRecordIfMissing(n.value)
                    } else {
                        downloadServerWithTrust(n.value)
                    }

                } catch (e: Exception) {
                    logger.e(e) { "Failed to start connection" }
                }
            }

            job.invokeOnCompletion { jobs.remove(node.id) }
            jobs[node.id] = job
        }
    }

    private fun createNodeRecordIfMissing(node: Node) {
        if (!nodeManager.nodeAvailable(node.id)) {
            logger.i { "${node.details()}: created node file" }
            nodeManager.create(node)
        }
    }

    private suspend fun downloadServerCert(node: Node) {
        val meta = node.meta as ServerMetaData
        val url = URLBuilder(
            host = meta.resolvedHost(),
            port = meta.port,
            protocol = URLProtocol.HTTPS,
            pathSegments = listOf("trust")
        ).build()
        logger.i { "${node.details()}: getting peer certificate for $url" }
        trustHttpClient.fetchPeerCert(url)
    }

    private suspend fun downloadServerWithTrust(node: Node) {
        try {
            nodeHttp.readHealth(node)?.let { server ->
                nodeManager.update(server)
                logger.i { "${node.details()}: downloaded server with ${(node.meta as ServerMetaData).nodes.size} nodes" }
            }
        } catch (e: Exception) {
            logger.e(e) { "${node.details()}: failed to download server" }
            throw e
        }
    }

    private suspend fun downloadServer(node: Node): Node? {
        try {
            return nodeHttp.readHealth(node)
        } catch (e: Exception) {
            logger.e(e) { "${node.details()}: failed to download server" }
            throw e
        }
    }

    private fun performConnectionSanityCheck(node: Node): Boolean {
        if (node.id == installId()) {
            logger.w("${node.details()}: blocked attempt to self connect")
            return false
        }

        if (jobs.containsKey(node.id)) {
            logger.i { "${node.details()} already connecting" }
            return false
        }

        return true
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertWireToServer(wire: NodeWire): Node {
        // If node already exists, return it — don't overwrite hostname from a new beacon
        if (nodeManager.nodeAvailable(wire.installId)) {
            return nodeManager.readNodeState(wire.installId).value
        }
        // Store raw hostname; isLocal=true ensures .local is appended in URL resolution
        val meta = ServerMetaData(name = wire.host(), port = wire.port, isLocal = true)
        return NodeBuilder().id(wire.installId).type(KrillApp.Server).parent(wire.installId).meta(meta)
            .host(wire.installId).create()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertMetaToServer(meta: ServerMetaData): Node {
        return NodeBuilder().id(TEMP_ID_FOR_CONNECTING).type(KrillApp.Server).parent(TEMP_ID_FOR_CONNECTING).meta(meta)
            .host(TEMP_ID_FOR_CONNECTING).create()
    }

    companion object {
        private const val TEMP_ID_FOR_CONNECTING = "TEMP_ID_FOR_CONNECTING"
    }
}
