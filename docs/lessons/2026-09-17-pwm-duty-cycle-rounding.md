---
issue: Sautner-Studio-LLC/krill-oss#245
pr: Sautner-Studio-LLC/krill-oss#245
date: 2026-09-17
module: krill-pi4j
category: other
title: "PWM duty cycle silently truncated to Int, always downward"
description: "DefaultPwmService dropped a requested PWM duty cycle's fractional percent via toInt() instead of rounding, biasing every channel low with no indication anything was quantized."
tags: ["krill-pi4j", "pwm", "duty-cycle", "rounding", "precision", "grpc"]
---

## What happened

`duty_cycle` is a `float` 0.0–100.0 on the wire, but Pi4J's native PWM API
(`Pwm.setDutyCycle`, `PwmConfigBuilder.dutyCycle`/`initial`, `Pwm.on`) only accepts
`Integer` percent — there is no float or nanosecond path anywhere in that interface.
`DefaultPwmService` accepted the caller's float precision and then discarded it with
`request.dutyCycle.toInt()` at four call sites (`configure()` ×3, `setDutyCycle()` ×1).
`Int.toInt()` on a `Float` truncates toward zero, so every fractional request rounded
**down**: `7.5f` became `7`, not `7` or `8` depending on nearest value. The bias was
systematic rather than symmetric, which made it indistinguishable from a trim offset
until the frequency changed — and the module's own `README.md` taught the exact
broken call (`dutyCycle = 7.5f`) as a servo centering example with no caveat that a
fifth of the requested precision would vanish.

## Fix

- `pi4j-ktx-service/krill-pi4j-service/src/main/kotlin/krill/zone/service/DefaultPwmService.kt` —
  replaced `request.dutyCycle.toInt()` with `request.dutyCycle.roundToInt()` at all four
  sites (`configure()`'s `dutyCycle(...)`, `initial(...)`, `ch.on(...)`, and
  `setDutyCycle()`'s `ch.on(...)`), turning a worst-case −1.0% bias into a ±0.5%
  symmetric one.
- `pi4j-ktx-service/krill-pi4j/src/main/proto/pi4j_service.proto` — added three
  additive, wire-compatible fields to `PwmResponse`: `requested_duty_cycle` (what the
  caller asked for), `quantized` (true when rounding changed the value), and
  `provider` (the resolved Pi4J provider id). `configure()` and `setDutyCycle()` now
  populate all three so a caller asking for `7.5` sees `actual=8, requested=7.5,
  quantized=true` instead of a bare `7` with no indication anything was dropped.
- `pi4j-ktx-service/README.md` — documented the Integer-percent limitation next to the
  PWM example and annotated the `7.5f` call with the value it actually rounds to.

Pi4J-native PWM still has 1% (200µs at 50Hz) granularity — no daemon-side fix changes
that; servo-grade resolution needs a separate pulse-width RPC. This issue only fixes
the truncation bias and makes the quantization observable on the wire.

## Prevention

A numeric conversion from a wire `float` to a platform `Integer` API should always be
an explicit rounding call (`roundToInt()`), never a bare `toInt()`/`toInt()`-equivalent
truncation — `toInt()` reads as "convert" but silently means "always round down" for
positive values. `DutyCycleRoundingTest` (in the same package as the fix) covers the
half-up boundary cases (`7.4→7`, `7.5→8`, `7.6→8`, `0.4→0`, `99.6→100`) against a mock
Pi4J context so a future regression here fails a unit test instead of shaving servo
travel on real hardware.
