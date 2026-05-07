# `KrillFeature` lacked a typed `requiresServer` field

**Issue:** [krill-oss#65](https://github.com/Sautner-Studio-LLC/krill-oss/issues/65)
**Upstream:** [krill#241](https://github.com/Sautner-Studio-LLC/krill/issues/241), consumed by [krill#237](https://github.com/Sautner-Studio-LLC/krill/issues/237)
**Root cause category:** Cross-repo schema drift — JSON resource added a field upstream that the typed DTO downstream had no surface for
**Module:** `module:krill-sdk`

## What happened

`Sautner-Studio-LLC/krill#241` added an explicit `requiresServer: Boolean` flag to every `KrillApp.*.json` so the recipe / FTUE work in `krill#237` could gate on which node types depend on a Krill server runtime. The JSON files live in the private `krill` repo, but the DTO they deserialise into — `KrillFeature` — lives here in `krill-sdk`. Without a corresponding typed field, every downstream consumer that wanted to read the new flag had to drop down to `JsonObject` parsing (which is what `krill`'s own guard test does, but it's the wrong shape for the recipe / FTUE call sites that are about to consume it).

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/feature/KrillFeature.kt` — added `requiresServer: Boolean`. **No default value**: krill#241 locked in "every JSON declares the flag explicitly," so a missing key on a typed bound deserialise should throw rather than silently default to `false` (which would silently mis-gate a server-dependent type as client-side).
- `krill-sdk/build.gradle.kts` — bumped `version` 0.0.17 → 0.0.18. The auto-dispatch workflow (see `2026-04-27-release-sdk-cross-repo-dispatch.md`) republishes on push to main; krill bumps `gradle/libs.versions.toml` once 0.0.18 is live on Maven Central.
- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/feature/KrillFeatureTest.kt` — three regression tests pin (a) `requiresServer = true` round-trips, (b) `requiresServer = false` round-trips, (c) deserialising a JSON object with the key omitted throws `SerializationException`. The third is the load-bearing one — it locks in the fail-loud contract the upstream JSONs depend on.

## Prevention

- **Required-no-default is the right shape for "every JSON must declare this" fields.** It pushes the failure to the deserialise call (one stack frame deep, with the field name in the message) instead of letting a wrong default propagate into gating logic where the symptom is a UI / FTUE bug far from the cause.
- **Cross-repo schema additions need a paired SDK PR even when the JSONs are happy with `JsonObject` parsing.** The upstream test using `JsonObject` is fine for guarding the JSONs themselves, but the recipe / FTUE consumers are typed call sites — letting them consume `JsonObject` would push the same untyped pattern across the codebase. Add the typed field in the same release window as the JSON-side change, even if the upstream PR can ship first.
- **Lock the "no default" intent with a deserialise test, not just a code comment.** A future drive-by edit could add `= false` thinking it's a small ergonomic improvement; the regression test makes that change red.
