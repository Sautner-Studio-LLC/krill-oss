---
issue: Sautner-Studio-LLC/krill-oss#259
pr: Sautner-Studio-LLC/krill-oss#261
date: 2026-09-17
module: krill-pi4j
category: packaging
title: "krill-pi4j postinst granted dialout/tty for a serial capability that doesn't exist"
description: "The Pi4J daemon's installer added its system user to dialout and tty groups, but Pi4J 4.0.0 has no serial I/O abstraction to use them."
tags: ["krill-pi4j", "packaging", "postinst", "serial", "least-privilege"]
---

## What happened

`krill-pi4j-service`'s Debian `postinst` added the `krill` system user to the
`dialout` and `tty` groups alongside `gpio`, `i2c`, and `spi` — implying the
daemon could talk to serial/UART devices. It cannot: Pi4J 4.0.0 (the version
this daemon is pinned to) has no `IOType.SERIAL` member and no
`com.pi4j.io.serial` package at all, so there was never a code path that used
those groups. `pi4j_service.proto` and `krill-pi4j-service/src` had no
`SerialService` and no serial-related code whatsoever — this wasn't a
partially-built feature, it was a permission grant with nothing behind it.

Investigating the underlying use case (originally raised in #250: "poll an
Atlas Scientific probe every five seconds") found it was already fully served
by `Server.SerialDevice` in the krill server — a pure-JVM `jSerialComm`
implementation that grants itself `dialout` independently in its own
`postinst`. Building a daemon-level `SerialService` would have meant
hand-rolling raw termios/ioctl bindings to bypass Pi4J's provider
abstraction entirely (which doesn't exist for serial), a real subsystem-sized
scope increase for a `severity:low` gap with no unserved use case behind it.

## Fix

Removed `dialout` and `tty` from both group lists in
`pi4j-ktx-service/krill-pi4j-service/package/DEBIAN/postinst` — the per-user
`adduser krill "$group"` loop and the systemd `SupplementaryGroups=` builder.
Left `gpio`, `i2c`, `spi`, `video`, and `plugdev` untouched since those back
real, exercised code paths (`GpioService`, `I2cService`, `SpiService`).

## Prevention

When a `postinst` grants a hardware group, treat it as a claim that some code
path in the same package uses it. Before adding a new group to an install
script, grep the service's own source for the capability it's meant to
unlock — if nothing calls it, the grant is dead weight that widens the
service account's attack surface for no functional benefit. This one had no
automated test guarding it (postinst scripts in this repo have no test
harness yet); `bash -n` on the script plus a manual read of the diff was the
verification available.
