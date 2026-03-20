package com.krillforge.pi4j

import com.google.protobuf.ByteString
import io.grpc.Channel
import com.krillforge.pi4j.proto.I2cByteResponse
import com.krillforge.pi4j.proto.I2cBytesResponse
import com.krillforge.pi4j.proto.I2cDeviceId
import com.krillforge.pi4j.proto.I2cReadBytesRequest
import com.krillforge.pi4j.proto.I2cRegisterRequest
import com.krillforge.pi4j.proto.I2cResponse
import com.krillforge.pi4j.proto.I2cServiceGrpcKt
import com.krillforge.pi4j.proto.I2cWriteBytesRequest
import com.krillforge.pi4j.proto.I2cWriteRegisterRequest

class I2cClient internal constructor(channel: Channel) {

    private val stub = I2cServiceGrpcKt.I2cServiceCoroutineStub(channel)

    /** Read one byte from a device register. Returns value 0–255 in [I2cByteResponse.value]. */
    suspend fun readRegister(bus: Int, address: Int, register: Int): I2cByteResponse =
        stub.readRegister(I2cRegisterRequest.newBuilder().apply {
            this.device   = deviceId(bus, address)
            this.register = register
        }.build())

    /** Write one byte (0–255) to a device register. */
    suspend fun writeRegister(bus: Int, address: Int, register: Int, value: Int): I2cResponse =
        stub.writeRegister(I2cWriteRegisterRequest.newBuilder().apply {
            this.device   = deviceId(bus, address)
            this.register = register
            this.value    = value
        }.build())

    /** Read [length] bytes starting at [register]. Raw bytes are in [I2cBytesResponse.data]. */
    suspend fun readBytes(bus: Int, address: Int, register: Int, length: Int): I2cBytesResponse =
        stub.readBytes(I2cReadBytesRequest.newBuilder().apply {
            this.device   = deviceId(bus, address)
            this.register = register
            this.length   = length
        }.build())

    /** Write a byte array starting at [register]. */
    suspend fun writeBytes(bus: Int, address: Int, register: Int, data: ByteArray): I2cResponse =
        stub.writeBytes(I2cWriteBytesRequest.newBuilder().apply {
            this.device   = deviceId(bus, address)
            this.register = register
            this.data     = ByteString.copyFrom(data)
        }.build())

    private fun deviceId(bus: Int, address: Int) =
        I2cDeviceId.newBuilder().setBus(bus).setAddress(address).build()
}
