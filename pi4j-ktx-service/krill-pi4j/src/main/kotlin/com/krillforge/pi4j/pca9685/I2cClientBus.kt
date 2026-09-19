package com.krillforge.pi4j.pca9685

import com.krillforge.pi4j.I2cClient
import java.io.IOException

/** Adapts [I2cClient] (the gRPC daemon client) to one fixed bus/address as an [I2cBus]. */
fun I2cClient.toI2cBus(bus: Int, address: Int): I2cBus = I2cClientBus(this, bus, address)

private class I2cClientBus(
    private val client: I2cClient,
    private val bus: Int,
    private val address: Int,
) : I2cBus {

    override suspend fun readRegister(register: Int): Int {
        val response = client.readRegister(bus, address, register)
        if (!response.success) fail("readRegister(0x${register.toString(16)})", response.message)
        return response.value
    }

    override suspend fun writeRegister(register: Int, value: Int) {
        val response = client.writeRegister(bus, address, register, value)
        if (!response.success) fail("writeRegister(0x${register.toString(16)}, 0x${value.toString(16)})", response.message)
    }

    override suspend fun readBytes(register: Int, length: Int): ByteArray {
        val response = client.readBytes(bus, address, register, length)
        if (!response.success) fail("readBytes(0x${register.toString(16)}, $length)", response.message)
        return response.data.toByteArray()
    }

    override suspend fun writeBytes(register: Int, data: ByteArray) {
        val response = client.writeBytes(bus, address, register, data)
        if (!response.success) fail("writeBytes(0x${register.toString(16)}, ${data.size} bytes)", response.message)
    }

    private fun fail(op: String, message: String): Nothing =
        throw IOException("I2C $op failed on bus $bus address 0x${address.toString(16)}: $message")
}
