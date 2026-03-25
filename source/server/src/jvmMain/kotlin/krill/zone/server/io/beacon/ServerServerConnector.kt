package krill.zone.server.io.beacon

import androidx.compose.runtime.*
import co.touchlab.kermit.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.server.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.peer.*
import krill.zone.shared.node.*
import org.koin.ext.*
import kotlin.uuid.*


/**
 * Source of starting a connection:
 * * Peer Beacon
 * * User Added with form
 * * App / Server Startup with stored server nodes
 */
class ServerServerConnector(

    private val nodeManager: ServerNodeManager,
    private val nodeHttp: NodeHttp,
    private val lanTrustTokenProvider: LanTrustTokenProvider,
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

                    // If we don't have an API key yet, attempt automatic handshake
                    if (!apiKeyAvailable(n.value)) {
                        val handshakeResult = attemptHandshake(n.value)
                        if (handshakeResult != null) {
                            // Store the peer's API key so subsequent calls are authenticated
                            val meta = n.value.meta as ServerMetaData
                            val updatedNode = n.value.copy(
                                meta = meta.copy(apiKey = handshakeResult.peerApiKey)
                            )
                            nodeManager.update(updatedNode)
                            n.value = updatedNode
                            logger.i { "${n.value.details()}: auto-trust handshake succeeded" }
                        } else {
                            logger.w { "${n.value.details()}: handshake failed, marking unauthorized" }
                            nodeManager.unauthorized(n.value)
                            return@launch
                        }
                    }

                    if (n.value.id == TEMP_ID_FOR_CONNECTING) {
                        //download the server info and replace
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

    /**
     * Step 2. Make sure we save and load the server
     */
    private fun createNodeRecordIfMissing(node: Node) {

        if (!nodeManager.nodeAvailable(node.id)) {
            logger.i { "${node.details()}: created node file" }
            nodeManager.create(node)
        }
    }


    /**
     *  Step 4. Download Cert if needed
     */
    private suspend fun downloadServerCert(node: Node) {
        val meta = node.meta as ServerMetaData
        val url = URLBuilder(
            host = meta.name,
            port = meta.port,
            protocol = URLProtocol.HTTPS,
            pathSegments = listOf("trust")
        ).build()
        logger.i { "${node.details()}: getting peer certificate for $url" }
        trustHttpClient.fetchPeerCert(url)
    }


    /**
     * Step 5. Download the server node - always overwrite local copies of this source of truth
     * if this was a server created locally, we need to update the install id from the server
     */
    private suspend fun downloadServerWithTrust(node: Node) {
        try {
            nodeHttp.readHealth(node )?.let { server ->
                nodeManager.update(server)

                logger.i { "${node.details()}: downloaded server with ${(node.meta as ServerMetaData).nodes.size} nodes" }



            }
        } catch (e: Exception) {
            logger.e(e) { "${node.details()}: failed to download server" }
            throw e
        }

    }

    /**
     * Step 5. Download the server node - always overwrite local copies of this source of truth
     */
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
        } //bounce self

        if (jobs.containsKey(node.id)) {
            logger.i { "${node.details()} already connecting" }
            return false
        } //already connecting

        // Allow through if we have an API key OR if auto-trust is available for handshake
        if (!apiKeyAvailable(node) && lanTrustTokenProvider.token() == null) {
            logger.d { "${node.details()} unauthorized (no API key and no LAN trust token)" }
            nodeManager.unauthorized(node)
            createNodeRecordIfMissing(node)
            return false
        }
        return true
    }

    private fun apiKeyAvailable(node: Node): Boolean {
        val meta = node.meta as ServerMetaData
        return meta.apiKey.isNotEmpty()


    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertWireToServer(wire: NodeWire): Node {
        val meta = ServerMetaData(name = wire.host(), port = wire.port)
        return NodeBuilder().id(wire.installId).type(KrillApp.Server).parent(wire.installId).meta(meta)
            .host(wire.installId).create()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertMetaToServer(meta: ServerMetaData): Node {

        return NodeBuilder().id(TEMP_ID_FOR_CONNECTING).type(KrillApp.Server).parent(TEMP_ID_FOR_CONNECTING).meta(meta)
            .host(TEMP_ID_FOR_CONNECTING).create()
    }

    /**
     * Attempt automatic peer handshake using the LAN trust token.
     * Returns the handshake response on success, or null on failure.
     */
    private suspend fun attemptHandshake(node: Node): HandshakeResponse? {
        val trustToken = lanTrustTokenProvider.token() ?: return null
        val peerMeta = node.meta as ServerMetaData

        // Get this server's identity for the handshake request
        val serverId = installId()
        val serverNode = nodeManager.readPersistedNode(serverId) ?: return null
        val serverMeta = serverNode.meta as ServerMetaData

        val request = HandshakeRequest(
            installId = serverId,
            hostname = serverMeta.name,
            port = serverMeta.port,
            peerApiKey = serverMeta.peerApiKey,
            lanTrustToken = trustToken
        )

        return try {
            val url = "https://${peerMeta.name}:${peerMeta.port}/peer/handshake"
            logger.i { "${node.details()}: attempting auto-trust handshake with ${peerMeta.name}" }

            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(fastJson.encodeToString(HandshakeRequest.serializer(), request))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val body = response.body<String>()
                    fastJson.decodeFromString(HandshakeResponse.serializer(), body)
                }
                HttpStatusCode.Forbidden -> {
                    logger.w { "${node.details()}: handshake rejected (trust token mismatch or auto-trust disabled on peer)" }
                    null
                }
                else -> {
                    logger.w { "${node.details()}: handshake failed with status ${response.status}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.w { "${node.details()}: handshake failed: ${e.message}" }
            null
        }
    }

    companion object {
        private const val TEMP_ID_FOR_CONNECTING = "TEMP_ID_FOR_CONNECTING"
    }


}
