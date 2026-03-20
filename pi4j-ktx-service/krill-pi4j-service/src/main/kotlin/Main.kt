package krill.zone

import io.grpc.ServerBuilder
import krill.zone.service.GpioServiceImpl
import krill.zone.service.I2cServiceImpl
import krill.zone.service.PwmServiceImpl
import krill.zone.service.SystemServiceImpl
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Pi4jService")

/**
 * Entry point for the Pi4J gRPC service daemon.
 *
 * Environment variables
 * ─────────────────────
 *   GRPC_PORT   TCP port to listen on             (default: 50051)
 *   PI4J_MOCK   "true" → start without hardware   (default: false)
 *
 * Command-line flags
 * ──────────────────
 *   --mock      equivalent to PI4J_MOCK=true
 *
 * Client usage
 * ────────────
 * Any JDK version can connect via the generated gRPC stubs; only *this*
 * daemon needs JDK 25 for Pi4J's Foreign Function & Memory API.
 */
fun main(args: Array<String>) {
    val port = System.getenv("GRPC_PORT")?.toIntOrNull() ?: 50051
    val mock = args.contains("--mock") || System.getenv("PI4J_MOCK") == "true"

    log.info("pi4j-ktx-service {} starting on port {} (mock={})", Version.SERVICE, port, mock)

    Pi4jContextManager.initialize(mock)

    val gpioService   = GpioServiceImpl(Pi4jContextManager)
    val pwmService    = PwmServiceImpl(Pi4jContextManager)
    val i2cService    = I2cServiceImpl(Pi4jContextManager)
    val systemService = SystemServiceImpl(Pi4jContextManager)

    val server = ServerBuilder.forPort(port)
        .addService(gpioService)
        .addService(pwmService)
        .addService(i2cService)
        .addService(systemService)
        .build()
        .start()

    // Let SystemService trigger a graceful shutdown via RPC
    systemService.server = server

    log.info("Listening on port {}", port)

    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("JVM shutdown hook — stopping server")
        server.shutdown()
        Pi4jContextManager.shutdown()
    })

    server.awaitTermination()
}
