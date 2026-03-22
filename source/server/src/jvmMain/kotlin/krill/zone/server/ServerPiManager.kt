package krill.zone.server

import co.touchlab.kermit.*
import com.krillforge.pi4j.Pi4jClient
import com.krillforge.pi4j.proto.PinState
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.*
import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.node.*
import krill.zone.shared.node.persistence.*
import org.koin.ext.*

/**
 * Tracks configuration state for a registered pin.
 */
@Serializable
data class RegisteredPinInfo(
    val pinNumber: Int,
    val bcmNumber: Int,
    val mode: Mode,
    val initialState: DigitalState,
    val shutdownState: DigitalState,
    val nodeId: String
)

class ServerPiManager(private val nodePersistence: NodePersistence, private val scope: CoroutineScope) : PiManager {
    private val logger = Logger.withTag(this::class.getFullName())

    /** Tracks registered pin configurations for change detection. */
    private val registeredPins = mutableMapOf<Int, RegisteredPinInfo>()

    /** Active watchInput jobs keyed by pin number — cancelled on unregister. */
    private val watchJobs = mutableMapOf<Int, Job>()

    private val mutex = Mutex()
    private lateinit var client: Pi4jClient

    /**
     * Called on server startup in Lifecycle.
     */
    override suspend fun init(callback: () -> Unit) {
        scope.launch {
            mutex.withLock {
                if (platform == Platform.RASPBERRY_PI && !::client.isInitialized) {
                    client = Pi4jClient() // localhost:50051
                    // Verify connectivity
                    try {
                        val ping = client.system.ping()
                        logger.i("Connected to krill-pi4j service v${ping.version}")
                    } catch (e: Exception) {
                        logger.e("Failed to connect to krill-pi4j service on localhost:50051", e)
                    }
                }
                callback()
            }
        }
    }

    override fun getServerInfo(): ServerInfo {
        return runBlocking {
            try {
                val info = client.system.getBoardInfo()
                ServerInfo(
                    model = info.boardModel,
                    os = info.operatingSystem,
                )
            } catch (e: Exception) {
                logger.e("Failed to get server info from krill-pi4j service", e)
                ServerInfo(model = "", os = "")
            }
        }
    }

    override fun readPinState(node: Node): DigitalState {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) {
            return DigitalState.OFF
        }
        val bcm = meta.address ?: return DigitalState.OFF
        return runBlocking {
            try {
                when (meta.mode) {
                    Mode.OUT -> {
                        val resp = client.gpio.getOutputState(bcm)
                        resp.state.toDigitalState()
                    }
                    Mode.IN -> {
                        val resp = client.gpio.getInput(bcm)
                        resp.state.toDigitalState()
                    }
                    Mode.PWM -> DigitalState.OFF
                }
            } catch (e: Exception) {
                logger.e("Failed to read pin state for BCM $bcm: ${e.message}")
                DigitalState.OFF
            }
        }
    }

    override fun setHigh(node: Node) {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) return
        val bcm = meta.address ?: return
        runBlocking {
            try {
                client.gpio.setOutput(bcm, high = true)
                logger.i("Pin BCM $bcm → HIGH")
            } catch (e: Exception) {
                logger.e("Failed to set pin BCM $bcm HIGH: ${e.message}")
            }
        }
        // Post event since we know the state changed
        scope.launch {
            EventFlowContainer.postEvent(Event(node.id, EventType.PIN_CHANGED, PinEventPayload(DigitalState.ON)))
        }
    }

    override fun setLow(node: Node) {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) return
        val bcm = meta.address ?: return
        runBlocking {
            try {
                client.gpio.setOutput(bcm, high = false)
                logger.i("Pin BCM $bcm → LOW")
            } catch (e: Exception) {
                logger.e("Failed to set pin BCM $bcm LOW: ${e.message}")
            }
        }
        // Post event since we know the state changed
        scope.launch {
            EventFlowContainer.postEvent(Event(node.id, EventType.PIN_CHANGED, PinEventPayload(DigitalState.OFF)))
        }
    }

    override fun addPinListener(node: Node, listener: StateChangeListener) {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) return
        val bcm = meta.address ?: return

        when (meta.mode) {
            Mode.IN -> {
                // Stream hardware state changes via gRPC watchInput
                val job = scope.launch {
                    try {
                        client.gpio.watchInput(bcm).collect { event ->
                            val state = event.state.toDigitalState()
                            logger.i("${node.details()}: input pin BCM $bcm state changed → $state")
                            listener.onStateChange(state)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.e("watchInput stream for BCM $bcm failed: ${e.message}")
                    }
                }
                watchJobs[meta.pinNumber] = job
            }
            Mode.OUT -> {
                // Output listeners are handled inline in setHigh/setLow via event posting
            }
            Mode.PWM -> {}
        }
    }

    override suspend fun getAllPins(): List<PinMetaData> {
        return try {
            val info = client.system.getBoardInfo()
            info.headerPinsList.map { pin ->
                val hardwareType = try {
                    HardwareType.valueOf(pin.pinType)
                } catch (_: IllegalArgumentException) {
                    HardwareType.GROUND
                }
                PinMetaData(
                    name = pin.name,
                    hardwareId = "GPIO_PIN_${pin.pinNumber}",
                    pinNumber = pin.pinNumber,
                    hardwareType = hardwareType,
                    address = if (pin.bcmNumber >= 0) pin.bcmNumber else null,
                    color = pin.color
                )
            }
        } catch (e: Exception) {
            logger.e("Failed to get board pins from krill-pi4j service", e)
            emptyList()
        }
    }

    override suspend fun getPins(): List<Node> {
        val registeredNodes = nodePersistence.loadByType(KrillApp.Server.Pin)
        val registeredByHardwareId = registeredNodes
            .filter { it.meta is PinMetaData }
            .associateBy { (it.meta as PinMetaData).hardwareId }

        return getAllPins()
            .filter { registeredByHardwareId.containsKey(it.hardwareId) }
            .mapNotNull { registeredByHardwareId[it.hardwareId] }
    }

    /**
     * Initialize pins on server startup.
     * Only registers pins that have been explicitly configured by users.
     */
    override suspend fun initPins() {
        logger.i(">>>>>>>>>>>>> init pins <<<<<<<<<<<<<<")
        mutex.withLock {
            val allPins = nodePersistence.loadByType(KrillApp.Server.Pin)
            val configuredPins = allPins.filter { (it.meta as PinMetaData).pinNumber > 0 }

            logger.i("Found ${configuredPins.size} configured pins out of ${allPins.size} total pins")

            configuredPins.forEach { node ->
                try {
                    val meta = node.meta as PinMetaData
                    if (meta.hardwareType.isDigital) {
                        registerPinInternal(node, meta)
                        logger.i("Registered configured pin ${meta.pinNumber} ${meta.name} ${meta.mode}")
                    }
                } catch (e: Exception) {
                    logger.e("Could not create Pin for node ${node.details()} due to ${e.message}", e)
                }
            }

            getPins()
                .filter { registeredPins.containsKey((it.meta as PinMetaData).pinNumber) }
                .forEach { addPinListenerInternal(it) }
        }
    }

    override suspend fun registerPin(node: Node) {
        mutex.withLock {
            val meta = node.meta as PinMetaData

            if (!meta.isConfigured) {
                logger.d("Pin ${meta.pinNumber} is not configured, skipping registration")
                return
            }

            if (meta.pinNumber in registeredPins) {
                logger.w("Pin ${meta.pinNumber} already registered, use reconfigurePin to update")
                return
            }
            if (!meta.hardwareType.isDigital) {
                logger.w("Pin ${meta.pinNumber} is not a digital pin, cannot register")
                return
            }

            try {
                registerPinInternal(node, meta)
                addPinListenerInternal(node)
                logger.i("Registered pin ${meta.pinNumber} with mode ${meta.mode}")
            } catch (e: Exception) {
                logger.e("Failed to register pin ${meta.pinNumber}: ${e.message}", e)
            }
        }
    }

    override suspend fun unregisterPin(node: Node) {
        mutex.withLock {
            val meta = node.meta as PinMetaData

            if (!meta.isConfigured) {
                return
            }

            if (meta.pinNumber !in registeredPins) {
                logger.w("Pin ${meta.pinNumber} is not registered, nothing to unregister")
                return
            }

            try {
                unregisterPinInternal(meta)
                logger.i("Unregistered pin ${meta.pinNumber}")
            } catch (e: Exception) {
                logger.e("Failed to unregister pin ${meta.pinNumber}: ${e.message}", e)
            }
        }
    }

    override suspend fun reconfigurePin(node: Node) {
        mutex.withLock {
            val meta = node.meta as PinMetaData

            if (!meta.isConfigured) {
                logger.d("Pin ${meta.pinNumber} is not configured, skipping reconfiguration")
                return
            }

            val existing = registeredPins[meta.pinNumber]

            if (existing != null && !hasConfigurationChanged(existing, meta)) {
                logger.d("Pin ${meta.pinNumber} configuration unchanged, skipping reconfiguration")
                return
            }

            if (!meta.hardwareType.isDigital) {
                if (existing != null) unregisterPinInternal(meta)
                return
            }

            try {
                if (existing != null) unregisterPinInternal(meta)
                registerPinInternal(node, meta)
                addPinListenerInternal(node)
                logger.i("Reconfigured pin ${meta.pinNumber} with mode ${meta.mode}")
            } catch (e: Exception) {
                logger.e("Failed to reconfigure pin ${meta.pinNumber}: ${e.message}", e)
            }
        }
    }

    override fun isPinRegistered(node: Node): Boolean {
        val meta = node.meta as PinMetaData
        return meta.pinNumber in registeredPins
    }

    // ── Internal helpers (must be called within mutex.withLock) ──────────

    /**
     * Register a pin with the krill-pi4j gRPC service.
     */
    private suspend fun registerPinInternal(node: Node, meta: PinMetaData) {
        val bcmNumber = meta.address ?: return

        when (meta.mode) {
            Mode.OUT -> {
                val initialHigh = meta.initialState == DigitalState.ON
                client.gpio.setOutput(bcmNumber, high = initialHigh)
            }
            Mode.IN -> {
                client.gpio.getInput(bcmNumber)
            }
            Mode.PWM -> logger.w("PWM mode not yet implemented for pin ${meta.pinNumber}")
        }

        registeredPins[meta.pinNumber] = RegisteredPinInfo(
            pinNumber = meta.pinNumber,
            bcmNumber = bcmNumber,
            mode = meta.mode,
            initialState = meta.initialState,
            shutdownState = meta.shutdownState,
            nodeId = node.id
        )
    }

    /**
     * Unregister a pin from the gRPC service.
     */
    private suspend fun unregisterPinInternal(meta: PinMetaData) {
        val bcm = meta.address
        if (bcm != null) {
            try {
                client.gpio.unregisterPin(bcm)
                logger.d("Released pin BCM $bcm from krill-pi4j service")
            } catch (e: Exception) {
                logger.w("Could not fully unregister pin BCM $bcm: ${e.message}")
            }
        }

        // Cancel any active watch job
        watchJobs.remove(meta.pinNumber)?.cancel()
        registeredPins.remove(meta.pinNumber)
    }

    private fun hasConfigurationChanged(existing: RegisteredPinInfo, newMeta: PinMetaData): Boolean =
        existing.mode != newMeta.mode ||
                existing.initialState != newMeta.initialState ||
                existing.shutdownState != newMeta.shutdownState

    /**
     * Add a state change listener that propagates hardware events back to the node system.
     */
    private fun addPinListenerInternal(node: Node) {
        addPinListener(node) { s ->
            nodePersistence.read(node.id)?.let { origin ->
                val meta = origin.meta as PinMetaData
                logger.i("${node.details()}: pin ${meta.pinNumber} state changed ${meta.state} --> $s")
                scope.launch {
                    EventFlowContainer.postEvent(Event(node.id, EventType.PIN_CHANGED, PinEventPayload(s)))
                }
            }
        }
    }
}

private fun PinState.toDigitalState(): DigitalState = when (this) {
    PinState.PIN_STATE_HIGH -> DigitalState.ON
    else -> DigitalState.OFF
}

/**
 * Convenience extension to check if a [HardwareType] supports digital I/O.
 */
private val HardwareType.isDigital: Boolean
    get() = this == HardwareType.DIGITAL || this == HardwareType.DIGITAL_AND_PWM
