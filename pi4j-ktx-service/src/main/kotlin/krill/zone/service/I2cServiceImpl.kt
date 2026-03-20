package krill.zone.service

import com.pi4j.io.i2c.I2C
import io.grpc.Status
import krill.zone.Pi4jContextManager
import krill.zone.pi4j.proto.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

import com.pi4j.ktx.io.i2c

/**
 * gRPC service for I2C bus operations.
 *
 * Devices are cached by (bus, address) so connections are reused across RPCs.
 * All operations are synchronous at the hardware level; they run in the gRPC
 * coroutine dispatcher and will not block the calling thread pool for long.
 */
class I2cServiceImpl(
    private val ctx: Pi4jContextManager = Pi4jContextManager
) : I2cServiceGrpcKt.I2cServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(I2cServiceImpl::class.java)

    /** Key: Pair(bus, address) */
    private val devices = ConcurrentHashMap<Pair<Int, Int>, I2C>()

    // ── ReadRegister ──────────────────────────────────────────────────────────

    override suspend fun readRegister(request: I2cRegisterRequest): I2cByteResponse = runCatching {
        val dev = device(request.device)
        val value = dev.readRegister(request.register)
        i2cByteResponse { success = true; this.value = value }
    }.getOrElse { e ->
        log.warn("readRegister {}: {}", request.device?.toKey(), e.message)
        i2cByteResponse { success = false; message = e.message.orEmpty() }
    }

    // ── WriteRegister ─────────────────────────────────────────────────────────

    override suspend fun writeRegister(request: I2cWriteRegisterRequest): I2cResponse = runCatching {
        val dev = device(request.device)
        dev.writeRegister(request.register, request.value.toByte())
        i2cResponse { success = true }
    }.getOrElse { e ->
        log.warn("writeRegister {}: {}", request.device?.toKey(), e.message)
        i2cResponse { success = false; message = e.message.orEmpty() }
    }

    // ── ReadBytes ─────────────────────────────────────────────────────────────

    override suspend fun readBytes(request: I2cReadBytesRequest): I2cBytesResponse = runCatching {
        val dev = device(request.device)
        val buf = ByteArray(request.length)
        dev.readRegister(request.register, buf, 0, request.length)
        i2cBytesResponse {
            success = true
            data    = com.google.protobuf.ByteString.copyFrom(buf)
        }
    }.getOrElse { e ->
        log.warn("readBytes {}: {}", request.device?.toKey(), e.message)
        i2cBytesResponse { success = false; message = e.message.orEmpty() }
    }

    // ── WriteBytes ────────────────────────────────────────────────────────────

    override suspend fun writeBytes(request: I2cWriteBytesRequest): I2cResponse = runCatching {
        val dev   = device(request.device)
        val bytes = request.data.toByteArray()
        dev.writeRegister(request.register, bytes, 0, bytes.size)
        i2cResponse { success = true }
    }.getOrElse { e ->
        log.warn("writeBytes {}: {}", request.device?.toKey(), e.message)
        i2cResponse { success = false; message = e.message.orEmpty() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun device(id: I2cDeviceId?): I2C {
        requireNotNull(id) { "I2cDeviceId is required" }
        return devices.getOrPut(id.bus to id.address) {
            log.debug("Opening I2C device bus={} addr=0x{}", id.bus, id.address.toString(16))
            ctx.context.i2c(id.bus, id.address) {
                this.id("i2c-${id.bus}-${id.address.toString(16)}")
            }
        }
    }

    private fun I2cDeviceId.toKey() = "bus=${bus} addr=0x${address.toString(16)}"
}
