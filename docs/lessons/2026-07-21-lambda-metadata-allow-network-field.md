---
issue: Sautner-Studio-LLC/krill-oss#213
pr: Sautner-Studio-LLC/krill-oss#213
date: 2026-07-21
module: krill-sdk
category: api-design
---

## What happened

Sautner-Studio-LLC/krill#918 (Lambda Python executor security audit) fixed the core
RCE-adjacent risk in `krill`'s server-side `SandboxConfig` — sandboxed execution now fails
closed when no firejail/docker is present, and `restrictNetwork` now defaults to `true`.
That fix is entirely server-side and correct as triage, but it left `restrictNetwork` as a
single server-wide switch: a Lambda that genuinely needs network access (e.g. to call an
external API) could only get it by disabling network restriction for *every* Lambda on that
server. `LambdaMetaData`, the per-node model this repo owns, had no field to express a
narrower opt-in.

## Fix

- Added `allowNetwork: Boolean = false` to `LambdaMetaData`
  (`krill-sdk/src/commonMain/kotlin/krill/zone/shared/krillapp/executor/lambda/LambdaMetaData.kt`),
  matching the "default to the more restrictive option, opt in explicitly" posture from
  krill#918. Defaulting to `false` means existing serialized `LambdaMetaData` payloads round-trip
  unchanged.
- Bumped `krill-sdk/build.gradle.kts` patch version (`0.0.61` → `0.0.62`) per the SDK
  versioning rule.
- Added `LambdaMetaDataTest` covering the default, back-compat deserialization of a payload
  missing the new field, and a full round-trip with it set.
- Did **not** touch `krill`'s `LambdaPythonExecutor.buildFirejailCommand`/`buildDockerCommand`
  or `EditLambda.kt` — those are `krill`'s files. A follow-up `krill` issue wires the sandbox
  command builders to read this per-node flag (falling back to the server-wide
  `SandboxConfig.restrictNetwork` when unset) instead of only the global default.

## Prevention

- **A server-wide safe default is not the same as a per-node override.** `krill#918`'s fix
  was correct triage — flip the global default to restrictive — but a single global switch
  can't express "most Lambdas should stay sandboxed, but this one legitimately needs a
  network call." When a security fix removes a capability wholesale, check whether the SDK
  model needs a narrower per-node escape hatch rather than leaving operators to choose
  between "insecure for everyone" and "broken for the one Lambda that needs network."
- **SDK model fields are the seam between repos.** `krill-oss` owns `LambdaMetaData`; `krill`
  owns the sandbox command builders that would read it. A field consumers need but can't
  reach isn't a `krill` bug — it's an SDK gap, and the fix belongs here even though the
  motivating incident was filed against `krill`.
