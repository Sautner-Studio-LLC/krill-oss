package krill.zone.shared.node.manager

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import krill.zone.shared.*
import krill.zone.shared.io.*
import krill.zone.shared.krillapp.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.*
import org.koin.ext.*
import kotlin.time.*
import kotlin.uuid.*

/**
 * Client NodeManager - optimized for driving Compose UI.
 *
 * ## Design Principles
 * - **In-memory StateFlows drive UI** - Compose observes these for reactive updates
 * - **SSE receives updates from server** - Updates flow in, update StateFlows, UI recomposes
 * - **REST APIs send changes to server** - User edits go out to server
 * - **File persistence for offline** - Local storage backup
 *
 * ## Flow
 * 1. SSE event received from server
 * 2. Update in-memory StateFlow
 * 3. Compose UI recomposes automatically
 * 4. User makes edit → REST API call to server
 * 5. Server processes, sends SSE back (loop to step 1)
 */
@OptIn(ExperimentalUuidApi::class)
class ClientNodeManager(
    private val fileOperations: FileOperations,
    private val nodeHttp: NodeHttp,
    private val observer: NodeObserver,
    private val scope: CoroutineScope
) {

    private val logger = Logger.withTag(this::class.getFullName())

    // In-memory state for UI reactivity
    private val nodes: MutableMap<String, MutableStateFlow<Node>> = mutableMapOf()
    private val _swarm = MutableStateFlow<Set<String>>(emptySet())
    private val _interactions = MutableStateFlow(emptyList<Node>())

    val swarm: StateFlow<Set<String>> = _swarm
    val interactions: StateFlow<List<Node>> = _interactions
    private val _selectedNodeId: MutableStateFlow<String?> = MutableStateFlow(null)
    val selectedNodeId: StateFlow<String?> = _selectedNodeId

    fun selectNode(nodeId: String?) {
        _selectedNodeId.value = nodeId
    }



    // ==================== Initialization ====================

    fun init(onReady: () -> Unit) {
        scope.launch {
            // Create client node if it doesn't exist
            logger.i("Initializing client node manager...")
            val client = ClientIdentity.getSelfWithInfo().copy(state = NodeState.NONE)
            fileOperations.update(client)
            update(client)

            // Wait for client node to be available
            scope.launch {
                try {
                    while (!nodes.containsKey(installId())) {
                        logger.w("Waiting for install id node")
                        delay(100)
                    }
                    onReady()
                } catch (e: Exception) {
                    logger.e("Critical: Client still null after initialization", e)
                }
            }
        }
    }

    // ==================== CRUD Operations ====================

    fun update(node: Node) {

        // Update or create StateFlow
        val flow = nodes.getOrPut(node.id) { MutableStateFlow(node).also { observeNode(it) } }
        flow.value = node

        // Update swarm for UI
        if (node.state != NodeState.DELETING) {
            _swarm.update { it.plus(node.id) }
        }

    }

    fun create(node: Node) {
        logger.i("Creating new node: ${node.details()}")
        submit(node)

    }

    fun delete(node: Node) {
        observer.remove(node.id)
        nodes.remove(node.id)
        _swarm.update { it.minus(node.id) }

        // Send delete request to server
        scope.launch {
            readNodeStateOrNull(node.host).value?.let { host ->
                nodeHttp.deleteNode(host, node)
            }
        }

        // Clean up local file for non-owned server nodes
        if (node.type is KrillApp.Server ) {
            fileOperations.delete(node.id)
        }
    }

    fun remove(id: String) {
        // Collect all node IDs to remove (including children)
        val idsToRemove = collectNodeIdsToRemove(id)

        // Remove all collected nodes
        idsToRemove.forEach { nodeId -> nodes.remove(nodeId) }

        // Update swarm in one batch
        _swarm.update { it.minus(idsToRemove) }

        // Notify observer for each removed node
        scope.launch {
            idsToRemove.forEach { nodeId -> observer.remove(nodeId) }
        }
    }

    private fun collectNodeIdsToRemove(rootId: String): Set<String> {
        val idsToRemove = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)

        // Snapshot to avoid concurrent modification
        val nodeSnapshot = nodes.values.map { it.value }.toList()

        while (queue.isNotEmpty()) {
            val currentId = queue.removeAt(0)
            idsToRemove.add(currentId)

            // Find children
            nodeSnapshot
                .filter { it.parent == currentId && it.id != currentId }
                .forEach { child ->
                    if (child.id !in idsToRemove) {
                        queue.add(child.id)
                    }
                }
        }
        return idsToRemove
    }

    // ==================== Read Operations ====================

    fun nodes(): List<Node> {
        return nodes.filter { it.value.value.state != NodeState.DELETING }.map { it.value.value }
    }

    fun nodeAvailable(id: String): Boolean {
        val node = nodes[id]?.value
        return node != null && node.state != NodeState.DELETING
    }

    fun readNodeState(id: String): StateFlow<Node> {
        return nodes[id] ?: throw Exception("Node not found: $id")
    }

    fun readNodeStateOrNull(id: String?): StateFlow<Node?> {
        if (id == null) return MutableStateFlow(null)
        if (!nodeAvailable(id)) return MutableStateFlow(null)
        return readNodeState(id)
    }

    fun children(node: Node): List<Node> {
        return nodes.values
            .filter { it.value.parent == node.id && it.value.state != NodeState.DELETING }
            .filter { it.value.type != KrillApp.Server && it.value.type != KrillApp.Client }
            .map { it.value }
    }


    fun getUpstreamDataPoint(node: Node): Node {
        val parent = readNodeState(node.parent).value
        return when (parent.type) {
            is KrillApp.DataPoint -> parent
            is KrillApp.Server -> throw Exception("Hit server while searching for upstream datapoint")
            else -> getUpstreamDataPoint(parent)
        }
    }

    // ==================== State Transitions ====================

    fun execute(node: Node) {
        logger.i("${node.details()}: Executing node")
        val current = nodes[node.id]?.value ?: return
        readNodeStateOrNull(node.id).value?.let { original ->
            if (original.state == NodeState.DELETING || original.state == NodeState.ERROR) {
                logger.w("${node.details()}: not executing node in invalid state")
                return
            }
        }

        val update = current.copy(
            state = NodeState.EXECUTED,

            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        update(update)

        if (node.type !is KrillApp.Client && node.type !is KrillApp.Server) {
            readNodeStateOrNull(node.host).value?.let { host ->

                scope.launch {
                    nodeHttp.postNode(host, update)
                }

            }
        }
    }


    fun reset(node: Node) {
        update(
            node.copy(
                state = NodeState.NONE,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }


    fun alarm(node: Node) {
        update(node.copy(state = NodeState.WARN))
    }

    fun run(node: Node) {
        update(node.copy(state = NodeState.INFO))
    }

    fun editing(node: Node) {
        update(node.copy(state = NodeState.EDITING, timestamp = Clock.System.now().toEpochMilliseconds()))
    }

    fun edited(node: Node) {
        update(node.copy(state = NodeState.USER_EDIT, timestamp = Clock.System.now().toEpochMilliseconds()))
    }

    fun submit(node: Node) {
        scope.launch {
            readNodeStateOrNull(node.host).value?.let { host ->

                update(
                    node.copy(
                        state = NodeState.NONE,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                )
                if (node.type is KrillApp.Server || node.type is KrillApp.Client) {
                    fileOperations.update(node)
                }
                nodeHttp.postNode(host, node)
            }
        }


    }

    fun unauthorized(node: Node) {
        update(
            node.copy(
                state = NodeState.UNAUTHORISED,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    fun startPairing(node: Node) {
        logger.d("Starting pairing: ${node.details()}")
        update(node.copy(state = NodeState.PAIRING, timestamp = Clock.System.now().toEpochMilliseconds()))
    }

    // ==================== Data Updates ====================

    /**
     * Update snapshot - posts to server and updates local state.
     * Called when user initiates a snapshot update (e.g., manual data entry).
     */
    fun postSnapshot(node: Node, snapshot: Snapshot) {

        readNodeStateOrNull(node.host).value?.let { host ->
            val meta = node.meta as DataPointMetaData
            val update = node.copy(
                state = NodeState.EXECUTED,

                meta = meta.copy(snapshot = snapshot),
                timestamp = Clock.System.now().toEpochMilliseconds()

            )
            scope.launch {
                nodeHttp.postNode(host, update)
            }
        }

    }


    fun updateSnapshot(node: Node, snapshot: Snapshot) {
        val meta = node.meta as DataPointMetaData
        update(node.copy(
                state = NodeState.EXECUTED,

                meta = meta.copy(snapshot = snapshot),
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }


    fun updateMetaData(node: Node, meta: NodeMetaData) {
        readNodeStateOrNull(node.host).value?.let {
            val update = node.copy(meta = meta, timestamp = Clock.System.now().toEpochMilliseconds())
            update(update)

            if (node.type is KrillApp.Server || node.type is KrillApp.Client) {
                fileOperations.update(update)
            }
        }


    }

    fun updateHandMadeServerWithRealData(node: Node, health: Node) {
        remove(node.id)
        delete(node)
        update(health.copy(timestamp = Clock.System.now().toEpochMilliseconds()))
    }

    // ==================== Observer/Lifecycle ====================

    fun observeNode(node: MutableStateFlow<Node>) {
        logger.i("Observing node ${node.value.details()}")
        observer.observe(node)
    }


    fun addInteraction(node: Node) {
        _interactions.update { it.plus(node) }
    }

    fun clearInteractions() {
        _interactions.update { emptyList() }
    }

}