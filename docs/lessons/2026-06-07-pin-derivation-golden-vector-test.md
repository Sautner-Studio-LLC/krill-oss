# PinDerivation: no test guarded the 4-platform byte-identical auth contract

**Issue:** [krill-oss#123](https://github.com/Sautner-Studio-LLC/krill-oss/issues/123)
**Root cause category:** Architectural gap — contract documented and enforced by convention but not by tests
**Module:** `module:krill-sdk`

## What happened

`PinDerivation` is the leaf primitive of the entire Krill auth layer: it turns the user's PIN into the Bearer token sent on every HTTP/SSE request and the rolling beacon token that gates swarm discovery. Its contract requires byte-identical output across four platform targets (JVM, Android, iOS, wasmJs) backed by three different crypto backends (`javax.crypto.Mac`, Apple CoreCrypto, and a hand-rolled pure-Kotlin SHA-256 + HMAC). Despite this unusually strict cross-platform requirement, `commonTest` had zero tests for `PinDerivation`. The hand-rolled wasmJs SHA-256 (90 lines of bit-twiddling) was one transposed constant away from producing a token no Krill server would ever accept, with a green test suite.

A nightly Kraken scan surfaced this gap alongside two related issues: the module-level docstring described the scheme as "PBKDF2-HMAC-SHA256" (wrong — it is a single un-iterated HMAC), and both `PinDerivation.ios.kt` and `PinDerivation.wasmJs.kt` contained dead `pbkdf2`/`pbkdf2HmacSha256` functions that were fully implemented but never called, creating a misleading signal about the actual scheme in use.

## Fix

- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/security/PinDerivationTest.kt` (new): 8 golden-vector tests exercising `deriveBearerToken` and `deriveBeaconToken` on every platform target. The expected values were computed against the Python `hmac`/`hashlib` standard library and will fail on any platform whose output drifts from the canonical result.
  - `deriveBearerToken("123456")` must equal `2d0f4836009a8c9d762995a16bad886ef77619c6c946cb52c798d389cc6cba97`
  - `deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-446655440000", 1700000000L)` must equal `c6aacfef`
  - Additional structural tests confirm 64-char / 8-char hex output, determinism, pin / UUID / window sensitivity, and the 30-second rotation boundary.
- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/security/PinDerivation.kt`: fixed module-level docstring — "PBKDF2-HMAC-SHA256" → "a single HMAC-SHA256 call".
- `krill-sdk/src/iosMain/kotlin/krill/zone/shared/security/PinDerivation.ios.kt`: removed dead private `pbkdf2(...)` function.
- `krill-sdk/src/wasmJsMain/kotlin/krill/zone/shared/security/PinDerivation.wasmJs.kt`: removed dead private `pbkdf2HmacSha256(...)` function.
- `krill-sdk/build.gradle.kts`: bumped version `0.0.43` → `0.0.44`.

## Prevention

- **Any expect/actual with a cross-platform byte-identical contract must have a commonTest golden-vector test.** The test is the only machine-enforceable guard; a convention comment in the source is not.
- **Before deleting dead code in a crypto primitive, check whether the dead function was the intended design.** Here the PBKDF2 code was vestigial — the scheme is a single HMAC and the docs were wrong. Either wire the intended algorithm in, or remove the dead code and correct the docs. Leaving both is the worst outcome: the docs lie, and the dead code signals the live code is wrong.
- **When a Nightly Bug Hunt files a lead against a crypto primitive, treat it as high-priority.** Auth failures surface only in production on the affected platform — the worst latency-to-discovery profile in the system.
