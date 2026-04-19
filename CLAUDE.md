# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

`krill-oss` is the public companion to the closed-source Krill automation system (see https://krillswarm.com). It is a collection of independent sibling projects, **not a single multi-module Gradle build**. There is no root `settings.gradle` or wrapper — always `cd` into the relevant sub-project before running Gradle.

- `pi4j-ktx-service/` — Buildable Gradle multi-module project. Contains the gRPC client library (`krill-pi4j`, published to Maven Central) and the daemon (`krill-pi4j-service`, distributed as a `.deb`). This is where the majority of Kotlin work happens.
- `krill-mcp/` — Buildable Gradle project that builds a Ktor-based Model Context Protocol server (`krill-mcp-service`) distributed as a `.deb`. Lets Claude Desktop / Claude Code talk to a Krill swarm via a remote MCP Custom Connector. Authenticates using the same PIN-derived bearer token as the rest of the swarm (HMAC-SHA256 with key `"krill-api-pbkdf2-v1"`) — any drift in `auth/PinDerivation.kt` breaks auth with every Krill server. See `krill-mcp/DEPLOYMENT.md` for the workflow snippet that belongs in the private `krill` repo.
- `source/` — **Source-only snapshot** of the closed-source Krill KMP app (`androidApp`, `composeApp`, `server`, `shared`). There is no `settings.gradle`, no `libs.versions.toml`, no wrapper, and the build files reference modules that aren't in this repo (`projects.generated`, `projects.ksp`). Treat it as read-only reference material — do not try to build it from here.
- `krill-sdk/` — Standalone JVM Kotlin scaffold, currently just a `Main.kt` hello-world. Has its own wrapper.
- `cookbook/` — Python scripts: `lambdas/` (examples for the Krill Python Lambda executor) and `python/` (hardware/audio experiments).
- `SVG Templates/` — Design assets.

## pi4j-ktx-service

This is the active Gradle project and the main thing you will build.

**Architecture.** Pi4J 4.0.0 uses the JDK Foreign Function & Memory API and requires JDK 25. Most Krill consumers are KMP projects stuck on JDK 21. So hardware access is split:

- `krill-pi4j` (client lib, JDK 21+) — thin gRPC client published as `com.krillforge:krill-pi4j`. Entry point `Pi4jClient` holds a `ManagedChannel` to `localhost:50051` and exposes four sub-clients: `gpio`, `pwm`, `i2c`, `system`. All ops are `suspend`. Proto contract lives at `krill-pi4j/src/main/proto/pi4j_service.proto` — both modules generate stubs from this file.
- `krill-pi4j-service` (daemon, JDK 25) — systemd service, shadowed fat JAR, runs Pi4J against real hardware. `Pi4jContextManager` owns the Pi4J context; `service/*ServiceImpl.kt` classes implement the gRPC surface.

The proto file is the single source of truth for the wire contract. Regenerate stubs (via the `protobuf` Gradle plugin) whenever it changes; both modules pick up changes automatically on build.

**Common commands** (run from `pi4j-ktx-service/`):

```bash
./gradlew :krill-pi4j:build                    # Build + test the client library
./gradlew :krill-pi4j-service:shadowJar        # Build the daemon fat JAR (→ build/libs/krill-pi4j-all.jar)
./gradlew :krill-pi4j:test --tests "FullyQualifiedClassName.testMethod"   # Single test
./gradlew :krill-pi4j:publishToMavenCentral    # Publish client (requires credentials)
```

**Toolchain requirements.** The client module pins `jvmToolchain(21)`; the service module pins `jvmToolchain(25)`. The foojay resolver plugin auto-provisions JDKs, but Gradle itself needs to run on a JDK ≥ the highest toolchain it will provision.

**Version catalog.** `gradle/libs.versions.toml` is the single place for dependency versions. Prefer `libs.plugins.*` / `libs.*` references over literals in build files.

## source/ (reference only)

The `source/` tree describes the Krill KMP application architecture, useful context when writing `krill-pi4j` client code that must integrate cleanly with Krill.

- `source/shared/` — KMP targets: `commonMain`, `androidMain`, `iosMain`, `jvmMain`, `wasmJsMain`. Shared data models and DI.
- `source/server/` — Ktor/Netty server. JVM-only. Entry point `krill.zone.Application.main` reads `/etc/krill/config.json`, boots Ktor with Koin modules (`ServerModule`, `ServerProcessModule`), Exposed/SQLite persistence (`db/`), and a beacon-based peer mesh (`io/beacon/`).
- `source/composeApp/` — Compose Multiplatform client. Targets Android, desktop (JVM), iOS (x64/arm64/simulatorArm64), and `wasmJs`. Desktop uses ProGuard via a `desktopFatJar` → `desktopProguard` → `desktopProductionJar` task chain (see `build.gradle.kts`). wasmJs builds respect the `fastWasm` Gradle property to toggle dev/prod webpack mode and skip Binaryen.
- `source/androidApp/` — Android application shell that depends on `composeApp` and `shared`. `minSdk` / `compileSdk` / `targetSdk` come from the version catalog.

Notable patterns used across `source/`:
- Koin for DI everywhere, including Compose previews via a `PreviewKoinContext` expect/actual composable (see `source/composeApp/PREVIEW_GUIDE.md`).
- `@Xexpect-actual-classes` is enabled — expect/actual classes, not just functions, are allowed.
- Server and client both use `io/beacon/` for peer discovery/connection; names like `Client*` vs `Server*` indicate which side owns a given component.

## cookbook/lambdas

Python scripts designed to be dropped into Krill's Python Lambda executor (see https://krillswarm.com/posts/2026/01/25/lambda-executor/). They are examples, not a package — no shared dependencies, no tests.
