package krill.zone.server

import co.touchlab.kermit.*
import com.pi4j.*
import com.pi4j.boardinfo.model.*
import com.pi4j.context.*
import com.pi4j.io.*
import com.pi4j.io.gpio.digital.*
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.ktx.io.digital.*
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
import java.util.*
import kotlin.uuid.*

private fun DigitalState.toPinState(): krill.zone.shared.node.DigitalState = when (this) {
    DigitalState.HIGH -> krill.zone.shared.node.DigitalState.ON
    else -> krill.zone.shared.node.DigitalState.OFF
}

private fun krill.zone.shared.node.DigitalState.toPi4j(): DigitalState = when (this) {
    krill.zone.shared.node.DigitalState.ON -> DigitalState.HIGH
    krill.zone.shared.node.DigitalState.OFF -> DigitalState.LOW
}

fun HeaderPin.toGpioPin(): PinMetaData {
    val hardwareType = HardwareType.valueOf(this.pinType?.name ?: HardwareType.GROUND.name)
    return PinMetaData(
        name = this.name.ifEmpty { "GPIO_${this.bcmNumber}" },
        hardwareId = "GPIO_PIN_${this.pinNumber}",
        pinNumber = this.pinNumber,
        hardwareType = hardwareType,
        address = this.bcmNumber,
        color = this.pinType.color.toString()
    )
}

/**
 * Tracks configuration state for a registered pin.
 */
@Serializable
 data class RegisteredPinInfo(
    val pinNumber: Int,
    val pi4jId: String,
    val mode: Mode,
    val initialState: krill.zone.shared.node.DigitalState,
    val shutdownState: krill.zone.shared.node.DigitalState,
    val nodeId: String
)

@OptIn(ExperimentalUuidApi::class)
class ServerPiManager(private val nodePersistence: NodePersistence, private val scope: CoroutineScope) : PiManager {
    private val logger = Logger.withTag(this::class.getFullName())

    /** Maps pinNumber → pi4j ID for registered pins. */
    private val ids = mutableMapOf<Int, String>()

    /** Tracks registered pin configurations for change detection. */
    private val registeredPins = mutableMapOf<Int, RegisteredPinInfo>()

    private val mutex = Mutex()
    private lateinit var pi: Context

    /**
     * Called on server startup in Lifecycle.
     */
    override suspend fun init(callback: () -> Unit) {
        scope.launch {
            mutex.withLock {
                if (platform == Platform.RASPBERRY_PI && !::pi.isInitialized) {
                    pi = Pi4J.newAutoContext()
                }
                callback()
            }
        }
    }

    override fun getServerInfo(): ServerInfo {
        val info = getBoardInfo()
        return ServerInfo(
            model = info.boardModel?.name.orEmpty(),
            os = info.operatingSystem?.name.orEmpty(),
        )
    }

    override fun readPinState(node: Node): krill.zone.shared.node.DigitalState {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) {
            return krill.zone.shared.node.DigitalState.OFF
        }
        when (meta.mode) {
            Mode.OUT ->  {
                val pin =  getPin<DigitalOutput>(meta, node) ?: return krill.zone.shared.node.DigitalState.OFF
                return pin.state().toPinState()
            }
            Mode.IN -> {
                val pin =  getPin<DigitalInput>(meta, node) ?: return krill.zone.shared.node.DigitalState.OFF
                return pin.state().toPinState()
            }
            Mode.PWM -> TODO()
        }
    }

    override fun setHigh(node: Node) {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) return
        val piPin = getPin<DigitalOutput>(meta, node) ?: return
        logger.i("dataengine: setting node ${piPin.id()} state high from ${piPin.state()}")
        piPin.high()
    }


    override fun setLow(node: Node) {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) return
        val piPin = getPin<DigitalOutput>(meta, node) ?: return
        logger.i("dataengine: setting node ${piPin.id()} state low from ${piPin.state()}")
        piPin.low()
    }

    override fun addPinListener(node: Node, listener: StateChangeListener) {
        val meta = node.meta as PinMetaData
        if (!meta.isConfigured) return
        when (meta.mode) {
            Mode.OUT -> getPin<DigitalOutput>(meta, node)?.listen { event ->
                logger.i("${node.details()}: output node state changed: ${event.state()}")
                listener.onStateChange(event.state().toPinState())
            }

            Mode.IN -> getPin<DigitalInput>(meta, node)?.listen { event ->
                logger.i("${node.details()}: input node state changed: ${event.state()}")
                listener.onStateChange(event.state().toPinState())
            }

            Mode.PWM -> TODO()
        }
    }

    fun getBoardInfo(): BoardInfo = pi.boardInfo()

    override suspend fun getAllPins(): List<PinMetaData> =
        getBoardInfo().boardModel.headerVersion.headerPins
            .flatMap { it.pins }
            .map { it.toGpioPin() }

    override suspend fun getPins(): List<Node> {
        val registeredNodes = nodePersistence.loadByType(KrillApp.Server.Pin)
        val registeredByHardwareId = registeredNodes
            .filter { it.meta is PinMetaData }
            .associateBy { (it.meta as PinMetaData).hardwareId }

        return getBoardInfo().boardModel.headerVersion.headerPins
            .flatMap { it.getPins() }
            .mapNotNull { pin -> registeredByHardwareId[pin.toGpioPin().hardwareId] }
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

    /**
     * Register a pin with pi4j based on its configuration.
     * Called when a new pin configuration is created.
     */
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

    /**
     * Unregister a pin from pi4j and remove its listener.
     * Called when a pin configuration is deleted.
     */
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
                unregisterPinInternal(meta.pinNumber)
                logger.i("Unregistered pin ${meta.pinNumber}")
            } catch (e: Exception) {
                logger.e("Failed to unregister pin ${meta.pinNumber}: ${e.message}", e)
            }
        }
    }

    /**
     * Reconfigure a pin if its mode or settings have changed.
     * This unregisters and re-registers the pin with new settings.
     * Called when a pin configuration is edited.
     */
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
                // If pin was previously registered but now isn't digital, unregister it
                if (existing != null) unregisterPinInternal(meta.pinNumber)
                return
            }

            try {
                if (existing != null) unregisterPinInternal(meta.pinNumber)
                registerPinInternal(node, meta)
                addPinListenerInternal(node)
                logger.i("Reconfigured pin ${meta.pinNumber} with mode ${meta.mode}")
            } catch (e: Exception) {
                logger.e("Failed to reconfigure pin ${meta.pinNumber}: ${e.message}", e)
            }
        }
    }

    /**
     * Check if a pin is currently registered with pi4j.
     */
    override fun isPinRegistered(node: Node): Boolean {
        val meta = node.meta as PinMetaData
        return meta.pinNumber in registeredPins
    }

    // ── Internal helpers (must be called within mutex.withLock) ──────────

    /**
     * Register a pin with pi4j using the Kotlin DSL.
     */
    private fun registerPinInternal(node: Node, meta: PinMetaData) {
        val pinNumber = meta.pinNumber
        val bcmNumber = meta.address ?: return
        val pi4jId = UUID.randomUUID().toString()
        ids[pinNumber] = pi4jId

        when (meta.mode) {
            Mode.OUT -> pi.digitalOutput(bcmNumber) {
                id(pi4jId)
                name(pi4jId)
                shutdown(meta.shutdownState.toPi4j())
                initial(meta.initialState.toPi4j())
            }

            Mode.IN -> pi.digitalInput(bcmNumber) {
                id(pi4jId)
                name(pi4jId)
            }

            Mode.PWM -> logger.w("PWM mode not yet implemented for pin $pinNumber")
        }

        registeredPins[pinNumber] = RegisteredPinInfo(
            pinNumber = pinNumber,
            pi4jId = pi4jId,
            mode = meta.mode,
            initialState = meta.initialState,
            shutdownState = meta.shutdownState,
            nodeId = node.id
        )
    }

    /**
     * Unregister a pin from pi4j.
     */
    private fun unregisterPinInternal(pinNumber: Int) {
        val pi4jId = ids[pinNumber] ?: return

        try {
            val io = pi.getIO<IO<*, *, *>>(pi4jId)
            if (io != null) {
                io.shutdown(pi)
                pi.registry().remove<IO<*, *, *>>(pi4jId)
                logger.d("Removed pin $pinNumber (id: $pi4jId) from Pi4J registry")
            }
        } catch (e: Exception) {
            logger.w("Could not fully unregister pin $pinNumber: ${e.message}")
        }

        ids.remove(pinNumber)
        registeredPins.remove(pinNumber)
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

    /**
     * Retrieve a registered I/O instance from the pi4j context by pin metadata.
     * Returns null if the pin is not registered. Pins must be registered through
     * [registerPin] or [reconfigurePin] which properly synchronize with the mutex.
     */
    private inline fun <reified T : IO<*, *, *>> getPin(meta: PinMetaData, node: Node? = null): T? {
        val pi4jId = ids[meta.pinNumber]
        if (pi4jId == null) {
            logger.w("Pin ${meta.pinNumber} (${meta.name}) is not registered. Register it first before using.")
            return null
        }
        return try {
            pi.getIO(pi4jId)
        } catch (e: Exception) {
            logger.e("Could not get pin ${meta.pinNumber} (${meta.name}): ${e.message}", e)
            null
        }
    }
}

/**
 * Convenience extension to check if a [HardwareType] supports digital I/O.
 */
private val HardwareType.isDigital: Boolean
    get() = this == HardwareType.DIGITAL || this == HardwareType.DIGITAL_AND_PWM

