package com.krillforge.pi4j

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Client for the krill-pi4j gRPC service.
 *
 * Requires JDK 21 or higher. The daemon it connects to runs on JDK 25 with
 * pi4j's Foreign Function & Memory API, but all client-side code is JVM 21 compatible.
 *
 * Usage:
 * ```kotlin
 * Pi4jClient().use { client ->
 *     client.gpio.setOutput(pin = 17, high = true)
 *     val state = client.gpio.getInput(pin = 27)
 *     client.gpio.watchInput(pin = 27).collect { event -> println(event) }
 * }
 * ```
 *
 * For long-lived connections, manage the lifecycle manually:
 * ```kotlin
 * val client = Pi4jClient(host = "raspberrypi.local", port = 50051)
 * // ... use client ...
 * client.close()
 * ```
 */
class Pi4jClient(
    host: String = "localhost",
    port: Int = 50051,
) : Closeable {

    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(host, port)
        .usePlaintext()
        .build()

    val gpio   = GpioClient(channel)
    val pwm    = PwmClient(channel)
    val i2c    = I2cClient(channel)
    val system = SystemClient(channel)

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}
