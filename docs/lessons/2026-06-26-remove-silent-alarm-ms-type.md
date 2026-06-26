# Stale node type advertised after upstream removal

**Issue:** [krill-oss#170](https://github.com/Sautner-Studio-LLC/krill-oss/issues/170)
**Root cause category:** Stale reference — SDK type removed without updating MCP type registry
**Module:** `krill-mcp`

## What happened

`KrillApp.Trigger.SilentAlarmMs` was removed from krill-sdk (commit `9a058ef`,
sdk 0.0.42, 2026-06-06) and from the krill server at the same time. The MCP
layer was not updated, so `list_node_types` continued advertising the type and
`KrillNodeTypes.kt` still held a full `KrillNodeType` block for it. Any agent
that attempted `create_node {type: "KrillApp.Trigger.SilentAlarmMs"}` received
a 400 from the krill server because the type no longer existed server-side.

The orphaned references spanned five locations:
- `KrillNodeTypes.kt`: four occurrences (validChildTypes on two container types,
  the full `KrillNodeType` block, and a validParentTypes entry on `KrillApp.Executor`)
- `SetNodeActionToolTest.kt`: one occurrence in the trigger-type enumeration
- Skill reference JSON files and INDEX.md: childTypes lists and a dedicated spec file

## Fix

- Removed all four occurrences in `krill-mcp-service/src/main/kotlin/krill/zone/mcp/krill/KrillNodeTypes.kt`.
- Removed the reference in `krill-mcp-service/src/test/kotlin/krill/zone/mcp/mcp/tools/SetNodeActionToolTest.kt`.
- Cleaned up skill docs: removed `SilentAlarmMs` from `KrillApp.DataPoint.json`
  and `KrillApp.Trigger.json` childTypes, removed the INDEX.md row, deleted
  `KrillApp.Trigger.SilentAlarm.json`, and scrubbed the mention in `mcp-tools.md`.

## Prevention

- When a node type is removed from krill-sdk / krill server, **update `KrillNodeTypes.kt`
  and the skill reference files in the same PR** — they form one logical registry.
  Leaving either stale causes agents to attempt creation of non-existent types.
- Consider a CI check that cross-validates `KrillNodeTypes.kt` `shortName` entries
  against the skill `references/node-types/` directory, so a deleted spec file
  would flag a stale registry entry (or vice versa) at build time.
