---
issue: Sautner-Studio-LLC/krill-oss#223
pr: Sautner-Studio-LLC/krill-oss#223
date: 2026-07-30
module: krill-mcp
category: api-design
---

## What happened

Phase 0+B of krill's `swarm-llm-workloads` change had already landed server-side: the
krill server ships `SwarmWork`/`SwarmBatch` dispatch machinery (arbitration, claim
windows, leases, fan-out) and blob-store routes (`POST /files`, `GET /files/{hash}`), and
krill-sdk 0.0.63 carries every wire contract (`SwarmWorkMetaData`, `SwarmBatchMetaData`,
`SwarmWorkPhase`, `FileRef`, the `LLMMetaData` swarm-availability block). None of it was
reachable from an agent session: `krill-mcp` had no tools that knew about `KrillApp.Swarm.*`
node types, so submitting swarm work or checking on it meant hand-crafting raw
`create_node`/`get_node` calls against an undocumented node shape. Until this landed, the
krill-side phases could only be validated by hand — this is also Ghost's verification
surface and the demo scripting surface for the whole swarm feature.

## Fix

- Added `SwarmTools.kt` (`krill-mcp-service/.../mcp/tools/`) with four tools:
  `submit_swarm_work`, `submit_swarm_batch`, `get_swarm_fleet`, `get_swarm_work_status`.
  Both submit tools create the node via the normal `POST /node/{id}` upsert *and* then
  call the new `KrillClient.invokeNode(id, by, verb)` (`POST /node/{id}/invoke`) with
  `EXECUTE`/`by=self` — creation alone only persists the node locally; the invoke is what
  makes the server's processor publish the `OPEN` state to swarm peers. A bare
  `create_node` would leave the work advertised to nobody.
- `submit_swarm_work` estimates payload size (prompt bytes + inline `images` + declared
  `fileRefs[].sizeBytes`) client-side against `maxPayloadBytes` and refuses before any
  network call — the server re-checks at creation and again at claim-assignment, but a
  local pre-check gives a faster, clearer failure for the common case of an oversized
  payload.
- `submit_swarm_batch` mirrors the server's `maxChildren` cap client-side so an oversized
  batch fails fast with an explicit error instead of being silently truncated (or
  discovered only after a round-trip).
- `get_swarm_fleet` projects `list_nodes` across every registered `KrillRegistry` client,
  filtered to `KrillApp.Server.LLM` nodes with `meta.swarmEnabled=true` — spans the whole
  reachable swarm in one call, not just one server.
- Bumped `krill-mcp/gradle/libs.versions.toml`'s `krill-sdk` pin from `0.0.48` to `0.0.63`
  to pick up the swarm contracts; this required a mechanical follow-on fix in
  `KrillNodeTypes.kt` (`LambdaSourceMetaData` was renamed to `LambdaMetaData` upstream
  between those SDK versions).
- Updated `skill/krill/SKILL.md` and `skill/krill/references/mcp-tools.md` with the new
  tools, a "Standard swarm dispatch flow", and the design-D13 payload-boundary rule: a
  file **path** in a prompt is inert to the model (no filesystem on the inference path) —
  content must travel as a `fileRefs` claim-check, pre-staged `meta.snapshot`, or small
  inline base64.
- `SwarmToolsTest.kt` covers input-schema shape and every pre-HTTP validation path
  (missing required fields, malformed `fileRefs`, oversized payload, over-cap batch) —
  this package has no `MockEngine` harness for `KrillClient`, so tests exercise the same
  "validate before resolve()" seam every other tool in this file uses, per the existing
  `SetValueToolTest`/`CreateNodeToolTest` pattern.

## Prevention

- When a krill-mcp tool wraps a node type whose processor requires an explicit invoke to
  take effect (anything with a "creation is inert, invoke publishes" contract), the tool
  must perform both steps atomically — documenting "remember to invoke after create" in a
  skill file is not a substitute for the tool doing it, because an agent given only
  `create_node` has no way to discover the missing step.
- Bumping a `krill-sdk` dependency pin across several releases at once (`0.0.48` →
  `0.0.63`, skipping over a dozen unreleased-to-this-repo point releases) can surface
  unrelated upstream renames as compile failures in unrelated files — budget time to scan
  the SDK's changelog/diff for renamed or removed types before assuming a pin bump is a
  pure addition.
- A pre-HTTP client-side validation pass (payload size, batch cap, required fields) is
  worth writing even when the server enforces the same invariant authoritatively — it
  turns a round-trip-then-fail into an immediate, cheap failure, and it's the only thing
  unit-testable without a live Krill server or a `MockEngine` harness in this package.
