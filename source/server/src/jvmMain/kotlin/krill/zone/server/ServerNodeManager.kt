package krill.zone.server

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.persistence.*
import org.koin.ext.*
import kotlin.time.*
import kotlin.uuid.*

/**
 * Server NodeManager - Simple database-backed node management for Ktor server.
 *
 * ## Design Principles
 * - **Database is the single source of truth** - No in-memory maps or caches
 * - **No actor pattern** - Direct database operations, simple and straightforward
 * - **SSE Integration** - All CRUD operations emit to SharedFlow for real-time client updates
 *
 * ## Flow
 * 1. Incoming update (REST API, sensor, hardware event)
 * 2. Write to database
 * 3. Emit to nodeUpdates SharedFlow (SSE broadcasts to clients)
 * 4. Trigger processor via observer (KrillApp.emit)
 * 5. Processor handles state, may write more updates (loop back to step 2)
 */
@OptIn(ExperimentalUuidApi::class)
class ServerNodeManager(
    private val nodePersistence: NodePersistence,
    private val nodeHttp: NodeHttp,
    private val dataStore: DataStore,
    private val piManager: PiManager,
    private val scope: CoroutineScope
) {

    private val logger = Logger.withTag(this::class.getFullName())

    /**
     * SharedFlow for broadcasting node updates to SSE clients.
     * Emits after every database mutation - the /sse route collects from this.
     */
    private val _nodeUpdates = MutableSharedFlow<Node>(
        replay = 0,
        extraBufferCapacity = 64
    )
    val nodeUpdates: SharedFlow<Node> = _nodeUpdates.asSharedFlow()

    // ==================== Initialization ====================

    fun init(onReady: () -> Unit) {
        scope.launch {
            try {
                // Ensure server node exists in database
                val serverInfo = ServerIdentity.getSelfWithInfo()
                nodePersistence.save(serverInfo)

                // Wait for server node to be available
                var attempts = 0
                while (nodePersistence.read(installId()) == null && attempts < 50) {
                    logger.d("Waiting for install id node")
                    delay(100)
                    attempts++
                }

                val server = nodePersistence.read(installId())
                    ?: throw Exception("Server node not found after initialization")

                if (server.state == NodeState.ERROR) {
                    logger.w("Server was in error state, resetting...")
                    nodePersistence.save(server.copy(state = NodeState.NONE))
                }

                onReady()
            } catch (e: Exception) {
                logger.e("Critical: Server initialization failed", e)
                throw e
            }
        }
    }


    fun setErrorState(node: Node, cause: String) {
        readNodeStateOrNull(node.id).value?.let { state ->
            if (state.state == NodeState.ERROR && state.meta.error == cause) {
                return
            } else {
                val updatedMeta = updateMetaWithError(node.meta, cause)
                update(
                    node.copy(
                        state = NodeState.ERROR,
                        meta = updatedMeta,
                        timestamp = Clock.System.now().toEpochMilliseconds()
                    )
                )
            }
        }

    }
    // ==================== CRUD Operations ====================

    fun update(node: Node) {

        readNodeStateOrNull(node.id).value?.let { origin ->

            if (node.state != NodeState.NONE && origin.state != node.state && node.timestamp - origin.timestamp > 1000) {
                logger.w("state changed ${origin.state} -> ${node.state} (${node.timestamp - origin.timestamp}ms)")
                scope.launch {
                    EventFlowContainer.postEvent(
                        Event(
                            node.id,
                            EventType.STATE_CHANGE,
                            StateChangeEventPayload(node.state)
                        )
                    )
                }

            }
        }
        nodePersistence.save(node)

        node.type.emit(node)


        scope.launch { _nodeUpdates.emit(node) }

        logger.d("Updated node: $node")
    }

    fun create(node: Node) {
        logger.i("Creating new node: ${node.details()}")
        update(node.copy(state = NodeState.CREATED))
    }

    fun delete(node: Node) {
        // Mark as deleting and broadcast
        val deletingNode = node.copy(state = NodeState.DELETING)
        nodePersistence.save(deletingNode)
        scope.launch { _nodeUpdates.emit(deletingNode) }


        // Get children before deleting
        val childIds = nodePersistence.loadAll()
            .filter { it.parent == node.id }
            .map { it.id }

        // Delete from database
        if (node.isMine()) {
            nodePersistence.delete(node.id)
        }


        // Recursively delete children
        childIds.forEach { childId ->
            nodePersistence.read(childId)?.let { delete(it) }
        }

        logger.i("Deleted node: ${node.details()}")
    }


    // ==================== Read Operations (Direct from DB) ====================

    fun nodes(): List<Node> {
        return nodePersistence.loadAll().filter { it.state != NodeState.DELETING }
    }

    fun nodesByType(type: KrillApp): List<Node> {

        return nodePersistence.loadByType(type).filter { it.state != NodeState.DELETING }
    }

    fun nodeAvailable(id: String): Boolean {
        val node = nodePersistence.read(id)
        return node != null && node.state != NodeState.DELETING
    }

    fun readNodeState(id: String): StateFlow<Node> {
        val node = readNode(id) ?: throw Exception("Node not found: $id")
        return MutableStateFlow(node)
    }

    /**
     * This is the source of truth for nodes.
     * TODO - read serial, web and any other data sources on reading
     */
    fun readNode(id: String): Node? {
        nodePersistence.read(id)?.let { node ->
            when (node.type) {
                KrillApp.DataPoint -> {
                    val meta = node.meta as DataPointMetaData
                    val snapshot = dataStore.last(node) ?: Snapshot(0L, 0.0)
                    return node.copy(meta = meta.copy(snapshot = snapshot))
                }

                KrillApp.Server.Pin -> {
                    val meta = node.meta as PinMetaData
                    if (!meta.isConfigured) {
                        return node
                    }
                    val state = piManager.readPinState(node)
                    return node.copy(meta = meta.copy(state = state))
                }

                else -> {
                    return node
                }
            }
        }
        return null

    }

    /**
     * Read a node directly from persistence without overlaying live hardware state.
     * Used by logic gates to read the persisted pin state (set by EventMonitor from
     * hardware events) rather than the live hardware which may have bounced.
     */
    fun readPersistedNode(id: String): Node? {
        return nodePersistence.read(id)
    }

    fun readNodeStateOrNull(id: String?): StateFlow<Node?> {
        if (id == null) return MutableStateFlow(null)
        val node = readNode(id)
        return if (node != null && node.state != NodeState.DELETING) {

            MutableStateFlow(node)
        } else {
            MutableStateFlow(null)
        }
    }

    fun children(node: Node): List<Node> {
        return nodePersistence.children(node)
    }

    suspend fun findNode(id: NodeIdentity): Node? {

        if (id.hostId == installId()) {
            return readNodeStateOrNull(id.nodeId).value
        }
        readNodeStateOrNull(id.hostId).value?.let { host ->
            logger.d("${host.details()}: Found host")

            try {
                nodeHttp.readNode(host, id.nodeId)?.let { node ->
                    logger.d("${node.details()}: found node!")
                    return node
                }
            } catch (e: Exception) {
                logger.e("${host.details()}: ${e.message}")
            }
        }

        return null
    }

    fun getUpstreamDataPoint(node: Node): Node {
        val parent = nodePersistence.read(node.parent)
            ?: throw Exception("Parent not found: ${node.parent}")
        return when (parent.type) {
            is KrillApp.DataPoint -> parent
            is KrillApp.Server -> throw Exception("Hit server while searching for upstream datapoint")
            else -> getUpstreamDataPoint(parent)
        }
    }

    // ==================== State Transitions ====================

    fun execute(node: Node) {
        val current = nodePersistence.read(node.id) ?: return
        readNodeStateOrNull(node.id).value?.let { origin ->
            if (origin.state == NodeState.DELETING || origin.state == NodeState.ERROR) {
                logger.w("${node.details()}: not executing node in invalid state")
                return
            }
        }

        update(
            current.copy(
                state = NodeState.EXECUTED,

                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    fun executeSources(node: Node, excludeIds: Set<String> = emptySet()) {
        nodePersistence.loadAll().filter { n ->
            n.state != NodeState.DELETING &&
                    n.meta is TargetingNodeMetaData
                    && (n.meta as TargetingNodeMetaData).sources.any { it.nodeId == node.id && it.hostId == node.host
                    && (n.meta as TargetingNodeMetaData).executionSource.contains(ExecutionSource.SOURCE_VALUE_MODIFIED) }
                    && n.id !in excludeIds
        }.forEach { target ->
            logger.i("---${node.details()}: executing targeting node ${target.id}")
            execute(target)
        }
    }

    fun executeChildren(node: Node) {
        children(node).forEach { child ->
            logger.d("${node.details()}: Triggering child ${child.name()} ${child.type}")


            scope.launch {
                if (child.meta is TargetingNodeMetaData && (child.meta as TargetingNodeMetaData).executionSource.contains(ExecutionSource.PARENT_EXECUTE_SUCCESS)) {
                    execute(child)
                } else if (child.meta !is TargetingNodeMetaData) {
                    execute(child)
                }
            }

        }
    }

    fun reset(node: Node) {
        val current = nodePersistence.read(node.id) ?: return
        if (current.state == NodeState.DELETING) return
        update(current.copy(state = NodeState.NONE, timestamp = Clock.System.now().toEpochMilliseconds()))
    }


    fun idle(node: Node) {
        update(node.copy(state = NodeState.PAUSED))
    }

    fun alarm(node: Node) {
        update(node.copy(state = NodeState.WARN))
    }

    fun unauthorized(node: Node) {
        update(
            node.copy(
                state = NodeState.UNAUTHORISED,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }


    fun updatePinState(node: Node, state: DigitalState) {
        val meta = node.meta as PinMetaData
        update(
            node.copy(
                meta = meta.copy(state = state),
                state = NodeState.EXECUTED,
                timestamp = Clock.System.now().toEpochMilliseconds()
            )
        )
    }

    fun updateMetaData(node: Node, meta: NodeMetaData) {
        update(node.copy(state = NodeState.NONE, meta = meta, timestamp = Clock.System.now().toEpochMilliseconds()))
    }


}