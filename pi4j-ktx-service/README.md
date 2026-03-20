# krill-pi4j

A gRPC microservice that exposes Raspberry Pi hardware — GPIO, PWM, and I2C — to any JVM application, regardless of Java version.

## Why this exists

[Pi4J v4](https://pi4j.com) uses the Foreign Function & Memory API (finalized in JDK 22) to talk directly to hardware. That makes it fast, but it means your entire application has to run on **JDK 25+** if you want to use it directly.

`krill-pi4j` solves this by running the Pi4J code in a small **daemon** (`krill-pi4j-service`) that speaks gRPC over localhost. Your application — a Ktor server, Spring Boot app, Android Things device, anything on the JVM — connects to it using the **client library** (`com.krillforge:krill-pi4j`) which requires only **JDK 21 or higher**.

```
Your app (JDK 21+)                    Raspberry Pi OS
┌─────────────────────┐               ┌──────────────────────────────┐
│  Pi4jClient (gRPC)  │─── localhost ─▶  krill-pi4j daemon (JDK 25) │
│  com.krillforge:    │    port 50051 │  ↳ Pi4J FFM API              │
│  krill-pi4j:0.0.1   │               │  ↳ GPIO / PWM / I2C          │
└─────────────────────┘               └──────────────────────────────┘
```

This project is a companion to the [Krill server](https://krillswarm.com) and is designed to be installed alongside it on Raspberry Pi hardware.

---

## Installing the daemon

The `krill-pi4j` service is distributed as a Debian package from the Krill APT repository.

**Full setup instructions:** [https://krillswarm.com/posts/2026/01/23/setup-deb/](https://krillswarm.com/posts/2026/01/23/setup-deb/)

Once the repository is configured:

```bash
sudo apt install krill-pi4j
```

This installs the fat JAR to `/usr/local/bin/krill-pi4j.jar`, registers a systemd service, and starts it automatically on port **50051**.

```bash
# Check the service
systemctl status krill-pi4j

# Follow logs
journalctl -u krill-pi4j -f

# Override the port (default: 50051)
sudo systemctl edit krill-pi4j
# Add: [Service]
#      Environment=GRPC_PORT=50052
```

The service requires **Java 25** on the Pi. Azul Zulu 25 for ARM64 is recommended.

---

## Client library

Add the dependency to your project:

**Gradle (Kotlin DSL)**
```kotlin
implementation("com.krillforge:krill-pi4j:0.0.1")
```

**Gradle (Groovy DSL)**
```groovy
implementation 'com.krillforge:krill-pi4j:0.0.1'
```

**Maven**
```xml
<dependency>
    <groupId>com.krillforge</groupId>
    <artifactId>krill-pi4j</artifactId>
    <version>0.0.1</version>
</dependency>
```

> **Requires JDK 21+.** The daemon on the Pi needs JDK 25, but your client application does not.

---

## Usage

`Pi4jClient` is the single entry point. It manages a gRPC channel to the daemon and exposes four sub-clients: `gpio`, `pwm`, `i2c`, and `system`.

### Connect

```kotlin
// Defaults: localhost:50051
Pi4jClient().use { client ->
    // ...
}

// Remote Pi on your network
val client = Pi4jClient(host = "raspberrypi.local", port = 50051)
```

All operations are `suspend` functions — call them from a coroutine scope.

---

### GPIO

```kotlin
Pi4jClient().use { client ->

    // Drive an output pin high (BCM pin numbers)
    client.gpio.setOutput(pin = 17, high = true)

    // Toggle it
    client.gpio.toggleOutput(pin = 17)

    // Pulse pin 17 HIGH for 500 ms, then revert to LOW
    client.gpio.pulse(pin = 17, durationMillis = 500, pulseHigh = true)

    // Read an input pin
    val response = client.gpio.getInput(
        pin = 27,
        pull = PullResistance.PULL_RESISTANCE_UP,
    )
    println("pin 27 is ${response.state}")

    // Stream state-change events — flow stays open until cancelled
    client.gpio.watchInput(pin = 27).collect { event ->
        println("pin ${event.pin} → ${event.state} at ${event.timestampNanos}")
    }
}
```

---

### PWM

```kotlin
Pi4jClient().use { client ->

    // Configure a servo on pin 18: 50 Hz, 7.5% duty cycle (centre position)
    client.pwm.configure(pin = 18, frequencyHz = 50, dutyCycle = 7.5f)

    // Move servo to ~0°
    client.pwm.setDutyCycle(pin = 18, dutyCycle = 5.0f)

    // Move servo to ~180°
    client.pwm.setDutyCycle(pin = 18, dutyCycle = 10.0f)

    // Dim an LED
    client.pwm.configure(pin = 12, frequencyHz = 1000, dutyCycle = 30.0f)
    client.pwm.setFrequency(pin = 12, frequencyHz = 2000)

    // Stop
    client.pwm.stop(pin = 18)

    // Read back current config
    val status = client.pwm.getStatus(pin = 18)
    println("running=${status.running} freq=${status.frequency} duty=${status.dutyCycle}")
}
```

---

### I2C

```kotlin
Pi4jClient().use { client ->

    // Read a single byte from bus 1, device 0x48, register 0x00
    val result = client.i2c.readRegister(bus = 1, address = 0x48, register = 0x00)
    if (result.success) println("value = ${result.value}")

    // Write a byte
    client.i2c.writeRegister(bus = 1, address = 0x3C, register = 0x00, value = 0xAE)

    // Read multiple bytes (e.g. a 6-byte accelerometer sample)
    val bytes = client.i2c.readBytes(bus = 1, address = 0x68, register = 0x3B, length = 6)
    println("raw: ${bytes.data.toByteArray().toHex()}")

    // Write a byte array
    client.i2c.writeBytes(
        bus = 1, address = 0x3C, register = 0x00,
        data = byteArrayOf(0xAE.toByte(), 0x00, 0xD5.toByte()),
    )
}
```

---

### System

```kotlin
Pi4jClient().use { client ->

    // Liveness check
    val pong = client.system.ping()
    println("service version: ${pong.version}, server time: ${pong.timestampMillis}")

    // Pi4J platform inventory
    val info = client.system.getInfo()
    println("pi4j version: ${info.pi4jVersion}")
    info.platformsList.forEach { println("  platform: $it") }
    info.providersList.forEach { println("  provider:  $it") }

    // Graceful remote shutdown
    client.system.shutdown()
}
```

---

## Project structure

```
pi4j-ktx-service/
├── krill-pi4j/                   # Client library (JVM 21, published to Maven Central)
│   └── src/main/
│       ├── proto/pi4j_service.proto
│       └── kotlin/com/krillforge/pi4j/
│           ├── Pi4jClient.kt
│           ├── GpioClient.kt
│           ├── PwmClient.kt
│           ├── I2cClient.kt
│           └── SystemClient.kt
└── krill-pi4j-service/           # Daemon (JVM 25, distributed as .deb)
    ├── package/DEBIAN/
    └── src/main/kotlin/krill/zone/
        ├── Main.kt
        ├── Pi4jContextManager.kt
        └── service/
            ├── GpioServiceImpl.kt
            ├── PwmServiceImpl.kt
            ├── I2cServiceImpl.kt
            └── SystemServiceImpl.kt
```

---

## Building from source

Requires JDK 25 for the service module (JDK 21 suffices for the client library alone).

```bash
# Build the client library JAR
./gradlew :krill-pi4j:build

# Build the fat JAR for the daemon
./gradlew :krill-pi4j-service:shadowJar

# Publish the client library to Maven Central (requires credentials)
./gradlew :krill-pi4j:publishToMavenCentral
```

---

## License

Apache License 2.0 — see [LICENSE](https://www.apache.org/licenses/LICENSE-2.0).

© 2026 [Benjamin Sautner](https://krill.zone)
