package com.krillforge.pi4j.pca9685

/**
 * In-memory [I2cBus] double. Records every register write (in order) so tests can assert exact
 * byte sequences, and echoes single-byte writes back on read so the sleep/prescale dance behaves
 * like a real chip — unless [ignoreWritesTo] says a register silently drops writes, simulating
 * the classic "PRE_SCALE write while not asleep" no-op bug.
 */
class FakeI2cBus(
    initialRegisters: Map<Int, Int> = emptyMap(),
    private val ignoreWritesTo: Set<Int> = emptySet(),
) : I2cBus {

    val registerWrites = mutableListOf<Pair<Int, Int>>()
    val blockWrites = mutableListOf<Pair<Int, ByteArray>>()

    private val registers = initialRegisters.toMutableMap()

    override suspend fun readRegister(register: Int): Int = registers.getOrDefault(register, 0)

    override suspend fun writeRegister(register: Int, value: Int) {
        registerWrites += register to value
        if (register in ignoreWritesTo) return
        registers[register] = value
    }

    override suspend fun readBytes(register: Int, length: Int): ByteArray = ByteArray(length)

    override suspend fun writeBytes(register: Int, data: ByteArray) {
        blockWrites += register to data
    }
}
