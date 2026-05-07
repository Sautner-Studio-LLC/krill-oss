# `MenuCommand` sealed class lacked a `KeepBuildingSwarm` discriminator

**Issue:** [krill-oss#67](https://github.com/Sautner-Studio-LLC/krill-oss/issues/67)
**Upstream:** [krill#255](https://github.com/Sautner-Studio-LLC/krill/issues/255) (Phase 0 of `local-first-onboarding`)
**Root cause category:** Cross-repo SDK extension — sealed-hierarchy member required upstream before the downstream Compose refactor can land
**Module:** `module:krill-sdk`

## What happened

`Sautner-Studio-LLC/krill#255` is refactoring `NodeChildren` so that clicking a `KrillApp.Client` node opens the FTUE walkthrough chooser. The trigger for that flow is a new `MenuCommand` discriminator — `KeepBuildingSwarm` — that the menu emits on the same SSE stream as real node events. Because the `MenuCommand` sealed class lives in `krill-sdk/src/commonMain/kotlin/krill/zone/shared/KrillApp.kt`, the new discriminator has to land here in the SDK and reach Maven Central before the downstream Compose refactor in the private `krill` repo can compile.

## Fix

- `krill-sdk/src/commonMain/kotlin/krill/zone/shared/KrillApp.kt` — added `@Serializable data object KeepBuildingSwarm : MenuCommand()` alongside the existing `Update` / `Delete` / `Expand` / `Focus` siblings. Pure additive: every existing consumer routes through `is MenuCommand` and gets the new subtype for free.
- `krill-sdk/build.gradle.kts` — bumped `version` 0.0.18 → 0.0.19. The auto-dispatch workflow (see `2026-04-27-release-sdk-cross-repo-dispatch.md`) republishes on push to main; the downstream `krill#255` tracker is explicitly blocked on a fresh artifact, so the bump is load-bearing — without it the change silently does not reach Maven Central.
- `krill-sdk/src/commonTest/kotlin/krill/zone/shared/MenuCommandTest.kt` — three small regression tests pin (a) `KeepBuildingSwarm` is reachable as a `MenuCommand` (and therefore `KrillApp.isMenuOption()` returns `true` for it), (b) the singleton identity contract holds, (c) `simpleName` is exactly `"KeepBuildingSwarm"`. The third is load-bearing because polymorphic serialisation registration in the consuming module's `Serializer.kt` keys on the symbol name — a future drive-by rename would silently break the wire shape.

## Prevention

- **Sealed-hierarchy additions for cross-repo flows ship as their own SDK PR.** The downstream Compose refactor in `krill#255` is a separate concern; bundling the sealed-class member with the consumer change would couple the SDK release cadence to whatever's happening on private-side feature branches. One PR per upstream sealed-class member, then bump and let the downstream pick up the artifact.
- **Don't trust the issue body's "no version bump required" note when the downstream tracker says it's blocked on a fresh artifact.** Both can't be true. The repo rule (every `krill-sdk/**` PR bumps the patch) is the source of truth — it's what actually drives `release-sdk.yml` to dispatch the publish workflow.
- **Polymorphic serialisation registration lives in the consumer (`Serializer.kt`), not the SDK.** The SDK only declares the discriminator; if a consumer forgets to register the subtype, the deserialise fails with a "polymorphic serializer was not found" runtime error rather than a compile error. Test in the consuming module that the new discriminator is registered — the `simpleName`-pin in this PR's test is a half-measure that catches a rename, not a missing registration.
