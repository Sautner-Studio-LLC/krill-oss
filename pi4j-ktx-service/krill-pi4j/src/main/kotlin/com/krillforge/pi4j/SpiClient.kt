package com.krillforge.pi4j

import com.google.protobuf.ByteString
import io.grpc.Channel
import com.krillforge.pi4j.proto.SpiDeviceId
import com.krillforge.pi4j.proto.SpiReadRequest
import com.krillforge.pi4j.proto.SpiResponse
import com.krillforge.pi4j.proto.SpiServiceGrpcKt
import com.krillforge.pi4j.proto.SpiTransferRequest
import com.krillforge.pi4j.proto.SpiTransferResponse
import com.krillforge.pi4j.proto.SpiWriteRequest

class SpiClient internal constructor(channel: Channel) {

    private val stub = SpiServiceGrpcKt.SpiServiceCoroutineStub(channel)

    /** Full-duplex transfer: write [data], simultaneously read back the same number of bytes. */
    suspend fun transfer(bus: Int, chipSelect: Int, data: ByteArray): SpiTransferResponse =
        stub.transfer(SpiTransferRequest.newBuilder().apply {
            this.device    = deviceId(bus, chipSelect)
            this.writeData = ByteString.copyFrom(data)
        }.build())

    /** Read-only transfer of [length] bytes (clocks out zeroes). */
    suspend fun read(bus: Int, chipSelect: Int, length: Int): SpiTransferResponse =
        stub.read(SpiReadRequest.newBuilder().apply {
            this.device = deviceId(bus, chipSelect)
            this.length = length
        }.build())

    /** Write-only transfer; any return data from the device is discarded. */
    suspend fun write(bus: Int, chipSelect: Int, data: ByteArray): SpiResponse =
        stub.write(SpiWriteRequest.newBuilder().apply {
            this.device = deviceId(bus, chipSelect)
            this.data   = ByteString.copyFrom(data)
        }.build())

    private fun deviceId(bus: Int, chipSelect: Int) =
        SpiDeviceId.newBuilder().setBus(bus).setChipSelect(chipSelect).build()
}
