/**
 * Direction of a `Server.SerialDevice` exchange — read a value off the
 * device, or write one to it. Selected per node; the server-side processor
 * dispatches to the appropriate jSerialComm code path based on this value.
 */
package krill.zone.shared.krillapp.server.serialdevice

/** Whether the serial device node is configured to read or write. */
enum class SerialDeviceOperation {
    READ, WRITE
}
