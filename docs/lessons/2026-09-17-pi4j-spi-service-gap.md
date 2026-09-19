---
issue: Sautner-Studio-LLC/krill-oss#250
pr: Sautner-Studio-LLC/krill-oss#260
date: 2026-09-17
module: krill-pi4j
category: missing-capability
title: "krill-pi4j daemon granted SPI/serial group permissions it had no service to use"
description: "postinst added the krill user to the spi and dialout groups, but no SpiService or SerialService existed to use those permissions."
tags: ["krill-pi4j", "spi", "serial", "pi4j", "missing-capability", "gpio"]
---

## What happened

`krill-pi4j-service`'s `postinst` added the `krill` system user to the `spi` and `dialout`
groups on install, but `pi4j_service.proto` only ever defined `GpioService`, `PwmService`,
`I2cService`, and `SystemService` — SPI was unreachable from Krill entirely, and serial only
existed as an unrelated ASCII poller (`Server.SerialDevice`) inside the separate `krill`
server, not through this daemon. The install granted a capability the daemon never
implemented.

Investigating the serial half turned up a second layer: Pi4J 4.0.0 (the version this daemon
is pinned to) has no serial I/O abstraction at all. `com.pi4j.io.IOType` — decompiled from
`pi4j-core-4.0.0.jar` — lists only `ANALOG_INPUT/OUTPUT`, `DIGITAL_INPUT/OUTPUT`, `PWM`,
`I2C`, `SPI`. There is no `SERIAL` member, no `com.pi4j.io.serial` package, and
`pi4j-plugin-ffm` ships only a low-level `Termios2` helper with no provider class wired into
the `Context`/`Provider`/registry system every other IOType goes through. SPI, by contrast,
is fully supported: `IOType.SPI` exists, `pi4j-plugin-ffm` ships `FFMSpiProviderImpl`
(`ffm-spi`), and `pi4j-plugin-mock` ships a matching `mock-spi` for tests.

## Fix

- Added `SpiService` to `krill-pi4j/src/main/proto/pi4j_service.proto` (`Transfer`, `Read`,
  `Write`, mirroring `I2cService`'s shape) and implemented it in
  `krill-pi4j-service/src/main/kotlin/krill/zone/service/DefaultSpiService.kt` — devices
  cached by `(bus, chipSelect)`, opened at Pi4J's default mode/baud, explicit
  `ffm-spi`/`mock-spi` provider selection via `Pi4jContextManager`.
- Added `IOType.SPI` to `Pi4jContextManager`'s `GUARDED_TYPES` and `MOCK_PROVIDER_IDS` so a
  missing SPI provider fails loud (`FAILED_PRECONDITION`) instead of silently no-op'ing
  against the `raspberrypi` platform's stub, per the pattern established in #244/#253.
  `FFM_PROVIDER_IDS` already had the `ffm-spi` mapping from #253's defensive addition; it
  was simply never guarded or exposed via a service.
- Added `SpiClient.kt` to the `krill-pi4j` client library and wired it into `Pi4jClient` as
  `client.spi`.
- Left serial out of scope — filed as #259 with the Pi4J-API-gap finding, so whoever picks
  it up starts from "this needs a design for raw termios I/O, not a mirror-the-pattern fix"
  instead of rediscovering the gap.

## Prevention

When a `postinst`/install script grants an OS permission (a group membership, a device-file
ACL) in anticipation of a capability, treat the absence of the corresponding service as a
tracked gap, not a silent no-op — the permission alone is misleading evidence that a feature
exists. Before implementing a hardware-facing service against a library like Pi4J, check the
library's actual type/provider inventory for the pinned version (`javap -p` a decompiled
core jar takes minutes) rather than assuming feature parity with adjacent bus types — SPI and
I2C look similar at the protocol level but were not similarly supported here.
