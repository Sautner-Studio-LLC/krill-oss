package com.krillforge.pi4j.pca9685

/**
 * Register-level I2C access to a single device address, decoupled from gRPC.
 *
 * [Pca9685Client] is written against this narrow contract instead of [com.krillforge.pi4j.I2cClient]
 * directly so tests can assert exact byte sequences against a fake bus — no daemon, no gRPC,
 * no hardware. The real implementation ([toI2cBus]) adapts [com.krillforge.pi4j.I2cClient] to one
 * bus/address pair and throws on a non-success response.
 */
interface I2cBus {
    suspend fun readRegister(register: Int): Int
    suspend fun writeRegister(register: Int, value: Int)
    suspend fun readBytes(register: Int, length: Int): ByteArray
    suspend fun writeBytes(register: Int, data: ByteArray)
}
