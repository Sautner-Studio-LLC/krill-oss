# krill-mcp wire schema drifted behind the server's observer-model refactor

**Issue:** (filed with the fix PR)
**Root cause category:** Schema drift — hand-maintained copies of wire types not updated with the upstream refactor
**Module:** `krill-mcp` (+ bundled `krill` skill)

## What happened

The krill server's unify-source-verb-wiring refactor (server ≥ 1.0.1108)
changed the node wire schema: `NodeState.CREATED` was replaced by
`CREATE_OR_OVERWRITE`, `executionSource` was renamed to `invocationTriggers`
(with new values `SOURCE_INVOKED` / `ON_CLICK`), `targets` was removed
entirely (wiring now lives on the observing node), `inputs` was added, and
trigger/filter thresholds moved into `meta.snapshot.value`. krill-mcp
v0.0.10 still posted the old shape from hand-maintained JSON skeletons in
`KrillNodeTypes.kt`, so every write tool (`create_node`, `create_project`,
`create_diagram`) failed against a current server with
`400 Failed to convert request body to class Node` — the removed `CREATED`
enum value alone is fatal under strict enum decoding. The bundled skill
documented the same stale model (parent-executes-children, `targets`,
5-field cron, `[node-id]` formula tokens), steering agents into the broken
calls and then into raw-REST workarounds.

## Fix

- `krill-mcp/gradle/libs.versions.toml`: bumped `krill-sdk` 0.0.24 → 0.0.47 —
  the SDK that now owns all MetaData data classes.
- `krill-mcp-service/.../krill/KrillNodeTypes.kt`: every `defaultMeta`
  skeleton is now serialized from the SDK data classes themselves
  (`Json { encodeDefaults = true }` + the class serializer), with the
  polymorphic discriminator injected; hints rewritten for
  `sources`/`inputs`/`invocationTriggers`/`nodeAction` and per-type facts
  (formula `[hostId:nodeId]` tokens over `inputs`, 6-field cron, thresholds
  in `meta.snapshot.value`, DataPoint ingest-from-first-data-source-input).
- `krill-mcp-service/.../mcp/tools/NodeTools.kt`: `create_node` posts
  `state=CREATE_OR_OVERWRITE`; `set_node_wiring` speaks
  `sources`/`inputs`/`invocationTriggers`/`nodeAction` and rejects the
  legacy `targets`/`executionSource` arguments with redirect hints.
- `krill-mcp-service/.../mcp/tools/DiagramTools.kt`,
  `krill/KrillClient.kt`: same `CREATED` → `CREATE_OR_OVERWRITE` fix for
  `create_project`/`create_diagram`.
- `krill-mcp-service/src/test/.../SetNodeWiringToolTest.kt`: contract tests
  for the new fields, the legacy-argument rejections, and
  no-`targets`/`executionSource`-in-skeleton assertions.
- `skill/krill/SKILL.md`, `references/mcp-tools.md`,
  `references/node-types/*.json` + `INDEX.md`: rewritten for the observer
  model (sources wake / inputs feed / snapshot publishes / verbs cascade /
  parent-child is visual only).

## Prevention

- **Never hand-copy wire types the SDK already defines.** Deriving
  `defaultMeta` from the SDK data classes makes the next schema change a
  one-line version bump instead of a silent drift — if the SDK and server
  agree, the MCP agrees by construction.
- **Reject removed wire fields loudly.** `set_node_wiring` now errors with a
  migration hint on `targets`/`executionSource` instead of letting
  `ignoreUnknownKeys` swallow them into a silent no-op.
- **When a refactor renames enum values used in hand-built JSON, grep every
  producer.** `state: "CREATED"` lived in three tools; strict enum decoding
  turns each one into a hard 400, and the OAuth-style error text from the
  server gives no hint which field was at fault.
