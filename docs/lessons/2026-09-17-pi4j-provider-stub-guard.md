---
issue: Sautner-Studio-LLC/krill-oss#244
pr: Sautner-Studio-LLC/krill-oss#244
date: 2026-09-17
module: krill-pi4j
category: missing-capability
title: "Pi4J PWM/I2C/GPIO reported success while silently no-op'ing against stub providers"
description: "krill-pi4j-service never selected a Pi4J provider, so hardware calls fell back to registry-only stubs that reported success without touching hardware."
tags: ["krill-pi4j", "missing-capability", "pi4j", "gpio", "pwm", "i2c", "silent-failure"]
---

## What happened

`krill-pi4j-service` never selected a Pi4J **provider** for any IO config. With no
`provider(...)` named at a call site, `Context.provider(IOType)` fell through to
`platform().provider(t)` — and the only platform on the classpath, `pi4j-plugin-raspberrypi`,
ships registry-only stub providers. `RpiPwm.on()` just sets a boolean field;
`RpiI2C.readRegister()` returns `0` unconditionally regardless of what's on the bus. Every
`PwmService.Configure`, `I2cService.ReadRegister`, and `GpioService.SetOutput` call reported
`success = true` while touching no hardware at all — confirmed live on a Pi 5 where `/dev/i2c-1`
didn't even exist and I2C reads still came back successful. `pi4j-plugin-ffm`, which Pi4J v4
actually ships real GPIO/PWM/I2C drivers in (via the Foreign Function & Memory API — the reason
this daemon is pinned to JDK 25 in the first place), was never even declared as a dependency.

A second, related bug lived in `Pi4jContextManager.buildMockContext()`: on
`ClassNotFoundException` for the (undeclared) mock plugin, it silently fell back to
`Pi4J.newAutoContext()` — "mock requested, real hardware context delivered." Same failure
shape as the provider bug: a caller's explicit choice silently became something else.

## Fix

- Declared `pi4j-plugin-ffm` as an `implementation` dependency of `krill-pi4j-service` and
  dropped `pi4j-plugin-pigpio` (Pi4J's own docs say it doesn't support the Pi 5).
- Added `Pi4jContextManager.ProviderFamily` (`FFM` default, `MOCK` for tests), selectable via
  `PI4J_PROVIDER`/`PI4J_MOCK`, and a per-`IOType` provider-id map confirmed against the real
  `pi4j-plugin-ffm-4.0.0.jar` via `javap` (`ffm-pwm`, `ffm-i2c`, `ffm-digital-input`,
  `ffm-digital-output`) — the `com.pi4j.ktx.utils.Provider` enum the ktx DSL exposes only has
  `mock-*`/`pigpio-*` entries, no FFM ones, so the ids had to be named as raw strings.
- Named the provider explicitly at all four IO call sites (`DefaultPwmService.configure()`,
  `DefaultI2cService.device()`, `DefaultGpioService.setOutput()`/`getOrCreateInput()`) via
  `IOConfigBuilder.provider(String)`.
- Added a startup guard: `Pi4jContextManager` resolves each guarded `IOType`'s intended
  provider once and records it as degraded if `context.hasProvider(id)` is false. Every call
  site checks this *before* touching hardware and throws `Status.FAILED_PRECONDITION` instead
  of proceeding — converting the silent lie into a loud, immediate error. A `runCatchingGrpc`
  helper (`krill/zone/service/GrpcSupport.kt`) ensures that guard exception isn't caught by the
  existing `runCatching { ... }.getOrElse { success = false } }` error-handling and folded back
  into a fake-successful-looking failure response.
- Deleted the mock-context silent fallback; a missing mock plugin now throws
  `IllegalStateException` instead of silently substituting a real hardware context.
- Added `ProviderResolutionTest` and `StubProviderGuardTest` — the module's first tests.

## Prevention

- **A platform default that resolves without error is not evidence it resolved correctly.**
  Pi4J's `Context.provider(IOType)` will happily hand back a stub if nothing more specific is
  configured — no exception, no warning, just wrong behavior. Any time a library exposes a
  "pick a default provider/implementation for me" call, verify by decompiling or testing what
  that default actually *does*, not just that it returns without throwing.
- **"The RPC exists and doesn't throw" is not an acceptance criterion for a hardware driver.**
  Two prior issues were closed on this exact code because the RPC surface existed. Acceptance
  for hardware I/O needs an assertion that the operation reached the device, not just that the
  call completed.
- **A silent fallback on `ClassNotFoundException` (or any "optional dependency missing" catch)
  is the same bug shape as a platform stub default** — a caller's explicit intent quietly
  becomes something else. Prefer failing loudly when an optional capability was explicitly
  requested but isn't available, over substituting a different behavior that merely happens not
  to crash.
- **When a real dependency's exact provider ids matter, verify them against the actual jar.**
  Library enums/DSL helpers lag behind what a provider plugin registers (here, the `pi4j-ktx`
  `Provider` enum had no FFM entries at all). `javap -c` on the resolved artifact is more
  trustworthy than a same-vintage convenience wrapper.
