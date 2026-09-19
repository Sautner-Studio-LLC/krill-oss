package krill.zone

import io.grpc.ServerBuilder
import krill.zone.service.GpioServiceImpl
import krill.zone.service.DefaultI2cService
import krill.zone.service.DefaultPwmService
import krill.zone.service.DefaultSpiService
import krill.zone.service.DefaultSystemService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Pi4jService")

/**
 * Entry point for the Pi4J gRPC service daemon.
 *
 * Environment variables
 * ─────────────────────
 *   GRPC_PORT      TCP port to listen on                        (default: 50051)
 *   PI4J_MOCK      "true" → start without hardware               (default: false)
 *   PI4J_PROVIDER  "FFM" or "MOCK" — provider family to select    (default: FFM)
 *
 * Command-line flags
 * ──────────────────
 *   --mock      equivalent to PI4J_MOCK=true
 *
 * `pi4j-plugin-mock` is a real `implementation` dependency of this module (krill-oss#264),
 * so `--mock`/`PI4J_MOCK=true` works in the installed daemon (shadowJar), not just under
 * the Gradle test task — needed on non-Pi hosts where the FFM plugin's hardware providers
 * can't initialize (e.g. missing `spi`/`gpio` OS groups). See [krill.zone.Pi4jContextManager].
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
    val pwmService    = DefaultPwmService(Pi4jContextManager)
    val i2cService    = DefaultI2cService(Pi4jContextManager)
    val spiService    = DefaultSpiService(Pi4jContextManager)
    val systemService = DefaultSystemService(Pi4jContextManager)

    val server = ServerBuilder.forPort(port)
        .addService(gpioService)
        .addService(pwmService)
        .addService(i2cService)
        .addService(spiService)
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
