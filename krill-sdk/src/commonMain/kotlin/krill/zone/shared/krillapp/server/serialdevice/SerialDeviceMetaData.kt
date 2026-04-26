/**
 * Metadata for a `Server.SerialDevice` node — represents one connected
 * serial peripheral on a Krill server (USB-serial adapter, RS-485
 * transceiver, etc.). Carries both the hardware identity and the full set
 * of serial-port tunables (baud rate, framing, flow control, encoding,
 * terminator) plus the application-level read/write configuration.
 */
package krill.zone.shared.krillapp.server.serialdevice

import kotlinx.serialization.*
import krill.zone.shared.krillapp.server.HardwareType
import krill.zone.shared.node.ExecutionSource
import krill.zone.shared.node.NodeIdentity
import krill.zone.shared.node.NodeState
import krill.zone.shared.node.TargetingNodeMetaData

/**
 * Payload for a `Server.SerialDevice` node.
 */
@Serializable
data class SerialDeviceMetaData(
    /** Display name shown on the device tile. */
    val name: String = "",
    /** Stable hardware identifier (typically the device path or serial number). */
    val hardwareId: String = "",

    /** Hardware classification — always `SERIAL` for this node type. */
    val hardwareType: HardwareType = HardwareType.SERIAL,
    /** Lifecycle state of the underlying jSerialComm port. */
    val status: NodeState = NodeState.CREATED,

    /** Polling interval for `READ` operations, in milliseconds. */
    val interval: Long = 5000L,
    /** Baud rate — `9600` is the safe default for unknown devices. */
    val baudRate: Int = 9600,
    /** Read timeout in milliseconds before the read coroutine gives up. */
    val readTimeout: Int = 1000,
    /** Write timeout in milliseconds. `0` means no timeout. */
    val writeTimeout: Int = 0,
    /** Whether this node reads from or writes to the device. */
    val operation: SerialDeviceOperation = SerialDeviceOperation.READ,
    /** Epoch millis of the most recent successful exchange. */
    val timestamp: Long = 0L,

    /** Data bits per frame. */
    val dataBits: SerialDataBits = SerialDataBits.EIGHT,
    /** Parity configuration. */
    val parity: SerialParity = SerialParity.NONE,
    /** Stop bits per frame. */
    val stopBits: SerialStopBits = SerialStopBits.ONE,
    /** Flow-control mode. */
    val flowControl: SerialFlowControl = SerialFlowControl.NONE,

    /** Charset for decoding / encoding payloads. */
    val encoding: SerialEncoding = SerialEncoding.UTF_8,
    /** Line terminator appended to outbound commands. */
    val terminator: SerialTerminator = SerialTerminator.CR,

    /**
     * Command sent on each poll when [operation] is `READ` — e.g. `"R"` for
     * Atlas Scientific sensors. Empty string is allowed for devices that
     * stream data without prompting.
     */
    val sendCommand: String = "R",

    override val sources: List<NodeIdentity> = listOf(NodeIdentity("", "")),
    override val targets: List<NodeIdentity> = emptyList(),
    override val executionSource: List<ExecutionSource> = emptyList(),
    override val error: String = "",
) : TargetingNodeMetaData
