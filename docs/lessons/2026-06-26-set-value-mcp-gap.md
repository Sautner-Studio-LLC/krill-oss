# Demo orchestrator fell back to wrong tool when no set_value MCP tool existed

**Issue:** [krill-oss#174](https://github.com/Sautner-Studio-LLC/krill-oss/issues/174)
**Root cause category:** Missing capability — no `set_value` MCP tool to receive imperative value-write actions from demo pipelines
**Module:** `krill-mcp`

## What happened

The kraken demo pipeline (`scripts/demo/`, scene `feed1`) issued a `set_value` action
(`target: 'Series', value: 10`) to feed live data into a compute-executor pipeline. Because no
`set_value` MCP tool existed, the orchestrator fell back to calling `set_node_wiring`, which
modifies observer wiring (sources/inputs/invocationTriggers) rather than recording a snapshot
value. The `set_node_wiring` call issued `GET /node/<uuid>` for the target and received
`404 Not Found` because the tool was the wrong choice entirely — it cannot set DataPoint values.

## Fix

- `krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/NodeTools.kt`:
  Added `SetValueTool` class implementing `Tool`. The tool accepts `target` (node id UUID
  or display name via `meta.name` resolution, matching `create_node`'s parent resolution
  pattern) and `value`, validates the resolved node is a `KrillApp.DataPoint`, coerces the
  value against its `dataType` (same logic as `record_snapshot`), and posts with
  `state=USER_EDIT` to run the server's filter/trigger pipeline.
- `krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/Main.kt`:
  Registered `SetValueTool` in the tool list.
- `krill-mcp/skill/krill/references/mcp-tools.md`:
  Added `set_value` documentation section before `record_snapshot`, with argument table,
  coercion rules, examples, and when-to-prefer guidance.
- New test `SetValueToolTest.kt`: pins the schema (required: target, value; optional:
  server, timestamp) and both pre-HTTP validation paths (missing target → error, missing
  value → error).

## Prevention

- Demo pipelines and automation scripts use named actions (`set_value`, `set_wiring`,
  etc.). When a new action category appears in demo scripts, ensure a correspondingly
  named MCP tool exists so orchestrators do not fall back to structurally similar but
  semantically wrong tools (e.g. `set_node_wiring` for a value-write operation).
- Both `set_value` and `record_snapshot` write DataPoint snapshots. The guidance in
  `mcp-tools.md`: use `set_value` for one-shot imperative writes (demo pipelines, name-
  only target); use `record_snapshot` for UUID-based batch/backfill semantics.
