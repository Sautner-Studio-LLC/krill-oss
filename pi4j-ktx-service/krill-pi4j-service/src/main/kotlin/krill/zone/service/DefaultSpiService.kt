@file:Suppress("DEPRECATION") // Pi4J 4.0.0 deprecated SpiChipSelect but ships no replacement API — see SpiConfigBuilder.chipSelect.

package krill.zone.service

import com.google.protobuf.ByteString
import com.krillforge.pi4j.proto.*
import com.pi4j.io.IOType
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiBus
import com.pi4j.io.spi.SpiChipSelect
import krill.zone.*
import org.slf4j.*
import java.util.concurrent.*

/**
 * gRPC service for SPI bus operations.
 *
 * Devices are cached by (bus, chipSelect) so connections are reused across RPCs — the
 * same shape [DefaultI2cService] uses for (bus, address). Opened at Pi4J's default mode
 * (MODE_0) and baud rate; this daemon does not yet expose a Configure RPC since no
 * consumer has needed non-default SPI timing.
 */
class DefaultSpiService(
    private val ctx: Pi4jContextManager = Pi4jContextManager
) : SpiServiceGrpcKt.SpiServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(DefaultSpiService::class.java)

    /** Key: Pair(bus, chipSelect) */
    private val devices = ConcurrentHashMap<Pair<Int, Int>, Spi>()

    // ── Transfer ──────────────────────────────────────────────────────────────

    override suspend fun transfer(request: SpiTransferRequest): SpiTransferResponse = runCatchingGrpc {
        val dev = device(request.device)
        val writeBytes = request.writeData.toByteArray()
        val readBytes = ByteArray(writeBytes.size)
        dev.transfer(writeBytes, 0, readBytes, 0, writeBytes.size)
        spiTransferResponse { success = true; readData = ByteString.copyFrom(readBytes) }
    }.getOrElse { e ->
        log.warn("transfer {}: {}", request.device?.toKey(), e.message)
        spiTransferResponse { success = false; message = e.message.orEmpty() }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    override suspend fun read(request: SpiReadRequest): SpiTransferResponse = runCatchingGrpc {
        val dev = device(request.device)
        val buf = ByteArray(request.length)
        dev.read(buf, 0, request.length)
        spiTransferResponse { success = true; readData = ByteString.copyFrom(buf) }
    }.getOrElse { e ->
        log.warn("read {}: {}", request.device?.toKey(), e.message)
        spiTransferResponse { success = false; message = e.message.orEmpty() }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    override suspend fun write(request: SpiWriteRequest): SpiResponse = runCatchingGrpc {
        val dev = device(request.device)
        val bytes = request.data.toByteArray()
        dev.write(bytes, 0, bytes.size)
        spiResponse { success = true }
    }.getOrElse { e ->
        log.warn("write {}: {}", request.device?.toKey(), e.message)
        spiResponse { success = false; message = e.message.orEmpty() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun device(id: SpiDeviceId?): Spi {
        requireNotNull(id) { "SpiDeviceId is required" }
        val providerId = ctx.requireProvider(IOType.SPI)
        return devices.getOrPut(id.bus to id.chipSelect) {
            log.debug("Opening SPI device bus={} cs={}", id.bus, id.chipSelect)
            val config = Spi.newConfigBuilder(ctx.context)
                .provider(providerId)
                .id("spi-${id.bus}-${id.chipSelect}")
                .bus(SpiBus.getByNumber(id.bus))
                .chipSelect(SpiChipSelect.getByNumber(id.chipSelect))
                .build()
            ctx.context.create(config, IOType.SPI)
        }
    }

    private fun SpiDeviceId.toKey() = "bus=${bus} cs=${chipSelect}"
}
