---
issue: Sautner-Studio-LLC/krill-oss#217
pr: Sautner-Studio-LLC/krill-oss#217
date: 2026-07-30
module: krill-sdk
category: api-design
---

## What happened

`Sautner-Studio-LLC/krill`'s `swarm-llm-workloads` openspec change (branch `a1`, cited commit
`6de9ee59e`) specs a new swarm-distributed LLM work-dispatch feature: nodes advertise
themselves as available LLM workers, and units of work (`SwarmWork`) are broadcast for a
remote node to claim, execute, and report back — with an optional "foreman" pattern where a
work unit can be decomposed into subtasks (`SwarmPlan`) and reassembled. This issue is phase A:
land the entire SDK contract layer in one release so every downstream phase (krill server
processors, Swarm Work UI, krill-mcp tools) pins one artifact instead of waiting on a second
SDK roll partway through the feature. The cited openspec branch had already merged into
`agents` (PR `krill#947`) by the time this issue was picked up, but the
`openspec/changes/swarm-llm-workloads/` directory itself was not present at that merge commit
or anywhere else in repo history — the issue body's own inline Kotlin snippets were complete
enough to implement from directly, so implementation proceeded from those rather than blocking
on an unreachable design doc.

## Fix

- Added `FileRef` (`krillapp/datapoint/FileRef.kt`) — a claim-check reference (hash, size,
  mime, host, name) — plus a new `DataType.FILE` enum entry, so a `Snapshot.value` can hold a
  serialized `FileRef` instead of ever carrying raw bytes in the state spine.
- Added `NodeAction.ADVERTISE` and `NodeAction.CLAIM`. `ServerNodeProcessor.onInvoke`'s default
  dispatch needed both added as explicit no-op branches (Kotlin's exhaustive `when` catches this
  at compile time) — a generic node processor "degrades to refusal" by doing nothing, per the
  issue's back-compat rule, rather than the compiler forcing a crash-shaped `else`.
- Added a new top-level `KrillApp.Swarm` sealed parent (not an `Executor.*` child) with `Work`
  and `Batch` children, registered in `krillAppChildren`. The issue posed this placement as an
  open design question with explicit guidance — prefer a new parent if more swarm types are
  anticipated — and the foreman/decomposition pattern plus the distinct claim/lease lifecycle
  (vs. `Executor`'s plain source-invoked contract) argue more types will follow.
- Added `SwarmWorkMetaData` and `SwarmBatchMetaData` (new `krillapp/swarm/` package), and
  `SwarmPlan`/`SubTask`/`Assembly` alongside `LLMResult` in `krillapp/server/llm/`. `SwarmBatch`'s
  exact field shape (item list + shared requirements + child cap + progress counts) was inferred
  from the issue's prose rather than a literal snippet — it's additive-only and easy to extend
  if the krill-side processor needs different shape.
- Added the swarm availability block to `LLMMetaData` (`swarmEnabled`, `advertisedModels`,
  `vramClass`, `costScore`, `costScoreSource`, `acceptWindowSource`, `advertisedAt`) — all
  defaulted so existing serialized nodes round-trip unchanged and every node opts out of swarm
  participation until explicitly enabled.
- `DataPointRelevance.relevantFilters`/`relevantTriggers` needed a `FILE` branch (no filters,
  `Timer` only — same treatment as `TEXT`/`JSON`) to keep the exhaustive `when` compiling.
- Bumped `krill-sdk/build.gradle.kts` patch version (`0.0.62` → `0.0.63`) per the SDK versioning
  rule.
- Added back-compat round-trip tests for every new/changed type, plus a `KrillAppSwarmTest`
  covering the new hierarchy registration and lookup, following the existing
  `SourceVerbWiringTest`/`LLMMetaDataTest` patterns (pre-change minimal payload → safe defaults;
  fully populated instance → round-trips).
- No polymorphic serializer-module registration in this PR — that module lives in `krill`'s
  consuming project, not in `krill-sdk` (confirmed: no `SerializersModule` exists anywhere in
  this repo). Per the issue's own sequencing note, krill-side phases pin this release next and
  register there.

## Prevention

- **An issue's own inline code snippets can outrank a cited external design doc.** When a
  cross-repo source-of-truth reference (branch/commit in another repo) turns out to be
  unreachable, check whether the filing issue is self-contained before treating the missing doc
  as a blocker — a fully-specified snippet-heavy issue body is often sufficient, and re-deriving
  the same contract from a design doc would just be redundant work.
- **A new enum value or sealed-class branch is a compile-time forcing function here, not a
  silent gap.** Every exhaustive `when` on `DataType` or `NodeAction` in this codebase is a
  deliberate guardrail (`DataPointRelevance`, `ServerNodeProcessor.onInvoke`) — expect the
  compiler to name every call site that needs an explicit decision when adding to either enum,
  and treat "what does this new value mean here" as a real design question at each site rather
  than reflexively adding a pass-through branch.
