---
issue: Sautner-Studio-LLC/krill-oss#264
pr: Sautner-Studio-LLC/krill-oss#265
date: 2026-09-18
module: krill-pi4j
category: packaging
title: "pi4j-plugin-mock was testImplementation-only, so PI4J_MOCK=true never worked in the installed daemon"
description: "krill-pi4j-service's --mock/PI4J_MOCK=true flag only worked under Gradle test; the shipped .deb had no way to run without real spi/gpio hardware groups."
tags: ["krill-pi4j", "packaging", "pi4j", "mock-provider", "ghost-runner", "ffm"]
---

## What happened

Ghost, verifying krill-oss#261 on the ghost runner (a non-Pi cloud VM with no
`spi`/`gpio` OS groups), found `krill-pi4j.service` logging repeated errors
from the Pi4J FFM plugin (`FFMPermissionHelper`, `DefaultRuntime`) followed by
every guarded `IOType` (digital in/out, PWM, I2C, SPI) coming up with no
working provider. `krill-pi4j-service`'s postinst only adds the `krill` user
to a hardware group when `getent group "$group"` succeeds; on the ghost
runner none of those groups exist, so the FFM plugin's own permission check
fails during `Pi4J.newAutoContext()` and every provider registration
cascades into failure.

The daemon has a `PI4J_MOCK=true`/`--mock` flag meant for exactly this case,
but `pi4j-plugin-mock` was a `testImplementation`-only Gradle dependency —
present on the test classpath, absent from the packaged shadowJar. The
installed `.deb` had no way to bring the service up without real hardware
groups at all.

## Fix

- `pi4j-ktx-service/krill-pi4j-service/build.gradle.kts` — moved
  `libs.pi4j.plugin.mock` from `testImplementation` to `implementation` so it
  ships in the shadowJar.
- `Pi4jContextManager.kt`'s `buildMockContext()` no longer reflectively loads
  `MockPlatform` (a workaround for the dependency not being on the runtime
  classpath); now that the mock jar is always present it directly registers
  the individual mock provider instances (`MockDigitalInputProviderImpl`,
  `MockDigitalOutputProviderImpl`, `MockPwmProviderImpl`, `MockI2CProviderImpl`,
  `MockSpiProviderImpl`) — mirroring the existing
  `ProviderResolutionTest.buildMockOnlyContext()` helper. Manual testing
  showed the platform-only registration this replaced never actually worked:
  every guarded `IOType` still came up degraded under `--mock`, because that
  code path had never been exercised end-to-end (the dependency it needed
  didn't exist on any runtime classpath until this fix).
- `Main.kt`'s doc comment updated to reflect that `--mock`/`PI4J_MOCK=true`
  now works in the installed daemon, not just under `./gradlew test`.
- `package/DEBIAN/postinst` now computes `PI4J_MOCK_DEFAULT` by probing for
  `gpio`/`spi` OS groups — the same signal the existing hardware-group loop
  already used — and writes it into the generated systemd unit's
  `Environment=PI4J_MOCK=` line instead of a hardcoded `false`. A host with
  neither group (ghost runner, dev laptop) now installs into working mock
  mode automatically, with no `Verify Agent Ghost.yml` or runner-provisioning
  change required; a real Pi image (which has these groups via
  `raspberrypi-sys-mods`) is unaffected and still gets `PI4J_MOCK=false`.

## Prevention

- Added `MockPluginPackagingTest` — a regression guard that fails the build
  if `pi4j-plugin-mock` ever regresses back to a `testImplementation`-only
  dependency, or if `postinst` ever regresses to an unconditional
  `PI4J_MOCK=false`.
- Added a test that calls the real `Pi4jContextManager.initialize(mock = true)`
  entry point (the one `Main.kt`'s `--mock` flag actually calls) and asserts
  every guarded `IOType` resolves. The previous tests only exercised a
  hand-built mock context via the `initializeForTest` seam, which never
  caught that the real `buildMockContext()` code path was broken — a
  reachability gap that let a genuinely non-functional feature ship for two
  releases. Prefer testing the actual public entry point a flag drives, not
  only an equivalent-looking test seam.
