---
issue: Sautner-Studio-LLC/krill-oss#246
pr: Sautner-Studio-LLC/krill-oss#246
date: 2026-09-17
module: krill-pi4j
category: missing-capability
title: "PWM's percent API cannot drive a servo — added a nanosecond SetPulse path"
description: "Pi4J's PWM interface is Integer-percent end to end, giving servos only ~5 commandable positions per millisecond of travel; SetPulse bypasses it via /sys/class/pwm."
tags: ["krill-pi4j", "missing-capability", "pwm", "servo", "esc", "sysfs", "raspberry-pi-5"]
---

## What happened

Pi4J 4.0.0's PWM interface is `Integer`-percent from top to bottom (`Pwm.setDutyCycle(Integer)`,
`PwmConfigBuilder.dutyCycle(Integer)`, `PwmConfig.dutyCycle()` all return `Integer`, confirmed via
`javap`). A hobby servo's useful range is ~1000 us of pulse width at 50 Hz; 1% of that period is
200 us, so the percent API can only address the servo's travel in steps of roughly a fifth of a
millisecond — about 5 usable positions across the full range. Rounding the duty cycle
(krill-oss#245) halves that quantization error but can't create resolution the interface never
carried. There was no way for `krill-pi4j` to drive a servo or ESC with usable precision.

## Fix

- Added `SetPulse(pin, period_ns, duty_ns)` to `PwmService` in
  `krill-pi4j/src/main/proto/pi4j_service.proto` — an exact-nanosecond path that bypasses Pi4J's
  PWM abstraction entirely.
- Implemented it in `DefaultPwmService.setPulse()`
  (`krill-pi4j-service/src/main/kotlin/krill/zone/service/DefaultPwmService.kt`) by writing
  directly to `/sys/class/pwm/pwmchipN/pwmM/{period,duty_cycle,enable}` — the RP1 hardware has
  nanosecond granularity there; only the Java API was in the way. The sysfs root is injected
  (`pwmSysfsRoot: File`, defaulting to `/sys/class/pwm`) so it can be pointed at a fake tree in
  tests instead of real hardware.
- `resolveChannelDir()` finds the single `pwmchipN` under the sysfs root and exports the
  requested channel if it isn't already exported. If no `pwmchipN` exists at all — the state of
  a Pi 5 that never got the PWM device-tree overlay — it throws `FAILED_PRECONDITION` with the
  exact remediation (`dtoverlay=pwm-2chan` in `/boot/firmware/config.txt` + reboot), the
  clarification that the RPC's `pin` is a PWM **channel** (0-3) and not a GPIO/BCM number, and
  the `dtparam=audio=on` conflict warning — plus a link to a new "Hardware PWM setup (Pi 5)"
  section in `pi4j-ktx-service/README.md` that spells all of this out.
- `Stop`/`GetStatus` now also track pulse-mode channels (a separate `pulseChannels` map, since
  they never go through Pi4J's own channel registry) so a servo driven via `SetPulse` can still
  be queried and stopped through the existing RPCs.
- Added `PwmClient.setPulse()` to the client library and a usage example in the README.
- The write sequence disables the channel and resets `duty_cycle` to 0 before writing a new
  `period`, then writes the target `duty_cycle` and re-enables — some sysfs PWM drivers reject
  a `period` write while `enable=1`, and all reject a `duty_cycle` write exceeding the
  currently-applied `period`. Both resets are no-ops on a freshly-exported channel, so the same
  sequence is safe for both first configuration and reconfiguring an already-running channel.
- `resolveChannelDir()` tolerates losing a concurrent-export race: if the `export` write fails
  (kernel EBUSY) but the channel directory exists anyway, that means another caller's export
  won the race and the channel is genuinely usable — only a still-missing directory is a real
  failure.
- `actualFrequency` in the response rounds (`Math.round(1e9 / periodNs)`) instead of truncating
  — the same truncation-vs-rounding mistake krill-oss#245 fixed for duty cycle, caught here by a
  code-reviewer subagent pass before merge rather than by a QA report.
- The sysfs write is injected as a constructor lambda (`sysfsWrite: (File, String) -> Unit`) so
  tests can simulate real kernel-driver rejection rules (period-while-enabled, duty>period) and
  the concurrent-export race deterministically, without touching real hardware.

## Prevention

- **A typed API's numeric type is part of its contract — check what resolution it can actually
  express before assuming "it has a percent knob" is good enough.** `Integer` duty-cycle percent
  reads as reasonable until you compute what one unit of it means in the physical units the
  consumer actually cares about (microseconds of servo travel).
- **An error message for a hardware setup trap is only as good as its remediation, and a stale
  link is worse than no link.** The missing-overlay error here embeds the exact config line and
  reboot requirement inline rather than only pointing at documentation, and the one link it does
  carry points at a README section shipped in the same commit — guaranteed to resolve, unlike a
  URL to a page that might not exist yet.
- **When a percent/generic API and an exact/raw API address logically the same hardware channel
  under the same `pin` field, keep a separate tracking map rather than overloading one.** Mixing
  Pi4J-registry-managed channels with raw-sysfs-managed ones in the same map would have made
  `Stop`/`GetStatus` silently wrong for whichever path wasn't the one that inserted the entry.
