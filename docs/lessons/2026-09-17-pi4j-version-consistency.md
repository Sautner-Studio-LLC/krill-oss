---
issue: Sautner-Studio-LLC/krill-oss#249
pr: Sautner-Studio-LLC/krill-oss#249
date: 2026-09-17
module: krill-pi4j
category: dependency-hygiene
title: "pi4j-ktx-service shipped four disagreeing version numbers"
description: "krill-pi4j's client lib, daemon, Version.kt, DEBIAN/control, and README each hand-maintained their own version, and drifted apart."
tags: ["krill-pi4j", "dependency-hygiene", "versioning", "packaging", "debian"]
---

## What happened

`pi4j-ktx-service` hand-maintained its version number in five places:
`krill-pi4j/build.gradle.kts` (`0.0.4`), `krill-pi4j-service/build.gradle.kts`
(`0.0.5`), `krill-pi4j-service/src/main/kotlin/krill/zone/Version.kt`
(`0.0.5`), `krill-pi4j-service/package/DEBIAN/control` (`0.0.4`), and three
dependency snippets in `pi4j-ktx-service/README.md` (`0.0.3`). They had
drifted independently — the deployed daemon reported `pi4j-ktx-service 0.0.5
starting on port 50051` while the `.deb` that installed it declared `0.0.4`,
so there was no single number a downstream consumer could point at and trust.
This is the same failure mode `krill-mcp` hit when its `DEBIAN/control`
`Version` sat frozen for months while every merged fix went unpublished.

## Fix

- Added a single `pi4jVersion` property to `pi4j-ktx-service/gradle.properties`.
- `krill-pi4j/build.gradle.kts` and `krill-pi4j-service/build.gradle.kts` now
  read `version = property("pi4jVersion") as String` instead of a hardcoded
  literal, so both modules always ship the same number.
- Bumped `krill-pi4j-service/package/DEBIAN/control`'s `Version` from `0.0.4`
  to `0.0.5` to match the already-deployed daemon and the new single source
  of truth.
- Fixed the three stale `0.0.3` dependency coordinates in
  `pi4j-ktx-service/README.md` to `0.0.5`.
- Added `VersionConsistencyTest` (mirrors the existing `GrpcVersionGuardTest`
  pattern) in `krill-pi4j-service/src/test/kotlin/krill/zone/`, which reads
  `gradle.properties`, both build files, `Version.kt`, and `DEBIAN/control`
  off disk and fails if any of them disagree with `pi4jVersion` again.

`Version.kt` and `DEBIAN/control` are still hand-maintained values rather
than generated files — this repo has no existing generated-Kotlin-source
pattern to build on, and `DEBIAN/control`'s actual packaging step lives in
the private `krill` repo's deploy workflow, out of scope here. The guard
test is the safety net for both until (if ever) that packaging step is
taught to template the version in directly.

## Prevention

Any module that ships more than one artifact from version-bearing files
(a Gradle `build.gradle.kts` version, a runtime version constant, a
packaging manifest) should derive all of them from one property and add a
guard test like `VersionConsistencyTest` — asserting equality on disk is
cheap and catches drift the moment a PR reintroduces a hand-typed literal,
long before anyone notices the shipped artifact disagrees with itself.
