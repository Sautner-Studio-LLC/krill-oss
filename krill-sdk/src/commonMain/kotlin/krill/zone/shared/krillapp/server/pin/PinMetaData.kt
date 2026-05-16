/**
 * Metadata for a `Server.Pin` node — represents one configured GPIO pin on
 * a Krill server. Carries both the user-facing identity (`name`,
 * `hardwareId`) and the runtime configuration (`mode`, `pinNumber`,
 * `hardwareType`) that the krill-pi4j daemon needs to claim and drive the
 * physical pin.
 */
package krill.zone.shared.krillapp.server.pin

import kotlinx.serialization.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.node.DigitalState
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeAction
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Server.Pin` node.
 */
@Serializable
data class PinMetaData(
    /** Display name shown on the pin tile. */
    val name: String = "",
    /** Stable hardware identifier (typically `"<host>:<header>:<pin>"`). */
    val hardwareId: String = "",
    /** Direction / capability — output, input, or PWM. */
    val mode: Mode = Mode.OUT,
    /** Current observed pin level. */
    val state: DigitalState = DigitalState.OFF,
    /** Level the pin is driven to immediately after configuration. */
    val initialState: DigitalState = DigitalState.OFF,
    /** Level the pin is driven to during graceful server shutdown. */
    val shutdownState: DigitalState = DigitalState.OFF,
    /** GPIO pin number on the header (1-based). `0` means "not configured yet". */
    val pinNumber: Int = 0,
    /** Hardware capability of the assigned pin — see [HardwareType]. */
    val hardwareType: HardwareType = HardwareType.GROUND,
    /** `true` if the user is allowed to change this pin's configuration in the editor. */
    val isConfigurable: Boolean = false,
    /** I²C / SPI bus address if the pin is part of a bus device — `null` for plain GPIO. */
    val address: Int? = null,
    /** Optional CSS colour hint shown on the pin tile. */
    val color: String? = null,
    override val error: String = "",
    override val sources: List<NodeIdentity> = emptyList(),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val nodeAction: NodeAction = NodeAction.EXECUTE,
) : TargetingNodeMetaData {
    /**
     * `true` when the pin has been assigned a real header position and a
     * non-default hardware id. Unconfigured defaults (`pinNumber == 0` and
     * empty `hardwareId`) should not be registered with the krill-pi4j daemon.
     */
    val isConfigured: Boolean
        get() = pinNumber > 0 && hardwareId.isNotEmpty()
}
