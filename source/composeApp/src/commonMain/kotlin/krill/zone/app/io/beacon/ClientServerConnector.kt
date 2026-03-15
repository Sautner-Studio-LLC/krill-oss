package krill.zone.app.io.beacon

import androidx.compose.runtime.*
import co.touchlab.kermit.*
import io.ktor.http.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.io.*
import krill.zone.shared.io.beacon.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import krill.zone.shared.node.manager.*
import org.koin.ext.*
import kotlin.uuid.*


/**
 * Source of starting a connection:
 * * Peer Beacon
 * * User Added with form
 * * App / Server Startup with stored server nodes
 */
class ClientServerConnector(
    private val sseBoss: SSEBoss,
    private val eventClient: EventClient,
    private val fileOperations: FileOperations,
    private val nodeManager: ClientNodeManager,
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
        logger.i("${node.details()}: connectNode")
        if (!performConnectionSanityCheck(node)) {
            return
        }

            mutex.withLock {
                if (jobs.containsKey(node.id)) {
                    logger.i { "${node.details()} already connecting (mutex check)" }
                    return@withLock
                }
                logger.w("${node.details()}: starting connection flow existing jobs: ${jobs.keys}")
                beginConnectionAttempt(node)
            }


    }

    private fun beginConnectionAttempt(node: Node) {
        logger.i("${node.details()}: begin connection attempt")
        val handler = CoroutineExceptionHandler { _, exception ->
            logger.e(exception) { "${node.details()} Uncaught exception in connection coroutine" }
            nodeManager.alarm(node)
        }
        val job = scope.launch(handler) {
            try {
                val n = mutableStateOf(node)
                logger.i { "${n.value.details()}: starting connection" }
                if (n.value.id != TEMP_ID_FOR_CONNECTING) {
                    createNodeRecordIfMissing(n.value)
                    updateApiKeysIfNewer(n.value)
                }

                val certReachable = downloadServerCert(n.value)
                if (!certReachable) {
                    logger.w { "${n.value.details()}: server unreachable, skipping connection" }
                    nodeManager.alarm(n.value)
                    return@launch
                }

                if (n.value.id == TEMP_ID_FOR_CONNECTING) {
                    //download the server info and replace

                    downloadServer(n.value)?.let { server ->
                        n.value = server
                        createNodeRecordIfMissing(server)
                        updateApiKeysIfNewer(server)
                    }

                } else {
                    downloadServerWithTrust(n.value)


                }

                downloadServerNodes(n.value)
                sseBoss.connect(n.value)
                eventClient.connect(n.value)
                nodeManager.reset(n.value)

            } catch (e: Exception) {

                logger.e(e) { "${node.details()} Failed to start connection" }
                nodeManager.alarm(node)
                if (e is CancellationException) throw e
            }
        }

        job.invokeOnCompletion { jobs.remove(node.id) }
        jobs[node.id] = job
    }


    /**
     * Step 2. Make sure we save and load the server
     */
    private fun createNodeRecordIfMissing(node: Node) {

        val stored = fileOperations.read(node.id)
        if (stored == null) {
            logger.i { "${node.details()}: created node file" }
            fileOperations.update(node)
        }

        if (!nodeManager.nodeAvailable(node.id)) {
            logger.i { "${node.details()}: created node record in node manager" }
            nodeManager.update(node)
        }

    }

    /**
     * Step 3. Update API Keys if present
     */
    private fun updateApiKeysIfNewer(node: Node) {
        val meta = node.meta as ServerMetaData
        nodeManager.readNodeState(node.id).value.let { state ->
            val storedMeta = state.meta as ServerMetaData
            if (storedMeta.apiKey.isNotEmpty() && storedMeta.apiKey != meta.apiKey) {
                logger.i { "${node.details()}: updated api key" }
                nodeManager.updateMetaData(state, storedMeta.copy(apiKey = meta.apiKey))
            }
        }
    }

    /**
     *  Step 4. Download Cert if needed
     *  Returns true if the server was reachable, false otherwise.
     */
    private suspend fun downloadServerCert(node: Node): Boolean {
        try {
            val meta = node.meta as ServerMetaData
            val url = URLBuilder(
                host = meta.name,
                port = meta.port,
                protocol = URLProtocol.HTTPS,
                pathSegments = listOf("trust")
            ).build()
            logger.i { "${node.details()}: getting peer certificate for $url" }
            return trustHttpClient.fetchPeerCert(url)
        } catch (e: Exception) {
            logger.e(e) { "${node.details()}: failed to download peer certificate" }
            return false
        }
    }


    /**
     * Step 5. Download the server node - always overwrite local copies of this source of truth
     * if this was a server created locally, we need to update the install id from the server
     */
    private suspend fun downloadServerWithTrust(node: Node) {
        try {
            nodeHttp.readNode(node, node.id)?.let { server ->
                fileOperations.update(server)
                nodeManager.update(server)
                logger.i { "${node.details()}: downloaded server with ${(node.meta as ServerMetaData).nodes.size} nodes" }


                val meta = server.meta as ServerMetaData
                nodeManager.nodes().filter { n -> n.host == server.host && n.id != server.id }.forEach { n ->
                    if (!meta.nodes.contains(n.id)) {
                        logger.i { "${node.details()}: deleting node ${n.id} from swarm" }
                        nodeManager.readNodeStateOrNull(n.id).value?.let { node ->
                            if (node.meta !is ServerMetaData) {
                                nodeManager.remove(n.id)
                            }
                        }

                    }
                }


            }
        } catch (e: Exception) {
            logger.e(e) { "${node.details()}: failed to download server" }
           // throw e
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
           return null
        }

    }

    /**
     * Step 6. Download nodes if app
     */
    private suspend fun downloadServerNodes(node: Node) {
        try {
            val nodes = nodeHttp.readNodes(node)
            nodes.forEach { node ->

                nodeManager.update(node)
            }
        } catch (e: Exception) {
            logger.e(e) { "${node.details()}: failed to download server nodes" }
        }


    }

    private fun performConnectionSanityCheck(node: Node): Boolean {
        if (node.id == TEMP_ID_FOR_CONNECTING) {
            logger.d { "${node.details()} connection attempt to manually entered node." }
            return true
        }
        if (node.id == installId()) {
            logger.w("${node.details()}: blocked attempt to self connect")
            return false
        } //bounce self

        if (sseBoss.isConnected(node.id)) {
            logger.d { "${node.details()} already connected" }
            return false
        } //already connected
        if (jobs.containsKey(node.id)) {
            logger.i { "${node.details()} already connecting" }
            return false
        } //already connecting


        if (!apiKeyAvailable(node)) {
            logger.d { "${node.details()} unauthorized" }
            nodeManager.unauthorized(node)
            createNodeRecordIfMissing(node)
            return false
        } //not worth trying
        return true
    }

    private fun apiKeyAvailable(node: Node): Boolean {
        val meta = node.meta as ServerMetaData
        return meta.apiKey.isNotEmpty()


    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertWireToServer(wire: NodeWire): Node {
        if (nodeManager.nodeAvailable(wire.installId)) {
            logger.d { "find known server during wire injest" }
            return nodeManager.readNodeState(wire.installId).value.copy(state = NodeState.PAIRING)
        }
        val name = if (wire.host().endsWith(".local")) {wire.host()} else { "${wire.host()}.local" }
        val meta = ServerMetaData(name = name, port = wire.port)

        return NodeBuilder().id(wire.installId).type(KrillApp.Server).state(NodeState.PAIRING).parent(wire.installId).meta(meta)
            .host(wire.installId).create()
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertMetaToServer(meta: ServerMetaData): Node {

        return NodeBuilder().id(TEMP_ID_FOR_CONNECTING).type(KrillApp.Server).parent(TEMP_ID_FOR_CONNECTING).meta(meta)
            .host(TEMP_ID_FOR_CONNECTING).create()
    }

    companion object {
        private val TEMP_ID_FOR_CONNECTING = "TEMP_ID_FOR_CONNECTING"
    }


}
