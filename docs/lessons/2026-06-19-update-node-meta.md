## What happened

The krill-mcp server had no way to change an existing node's metadata after creation. The only node write tools were `create_node` (creates, posts `state=CREATE_OR_OVERWRITE`), `set_node_wiring` (sources / inputs / invocationTriggers / nodeAction), `set_node_action` (nodeAction only), `record_snapshot` (DataPoint values), and the Diagram family. Fields like a CronTimer's `expression` or a Calculation's `formula` could only be set at `create_node` time via the `meta` overlay; once the node existed they were effectively immutable through MCP.

This surfaced when the kraken demo pipeline tried to show a "create a node, then configure it live" demo beat — drop a Cron node, then give it a `*/5 * * * * *` schedule. There was no tool for the second step.

## Fix

- Added `UpdateNodeTool` (`krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/NodeTools.kt`): fetches the existing node, shallow-merges the caller's `meta` overlay (skipping the polymorphic `type` key), and re-posts with `state=USER_EDIT`. Connected clients receive the update via SSE so the desktop UI reflects it live.
- Registered `UpdateNodeTool(registry)` in `Main.kt`'s `tools = listOf(...)`.
- Added `UpdateNodeToolTest` covering schema contract, missing-argument rejection, and empty-meta rejection.
- Updated `skill/krill/references/mcp-tools.md`: added `### update_node` section under "Generic node write tools"; removed stale "General update in place" gap entry from "What's NOT here yet".
- Updated `skill/krill/SKILL.md` step 5 of the multi-node authoring workflow to mention `update_node` as the post-creation configuration path.

## Prevention

- When adding a new node mutation path (wiring, action, state), also check whether a general meta-update path is missing — it usually is and causes the same class of gap.
- Keep the `What's NOT here yet` section in `mcp-tools.md` accurate: remove gap entries in the same PR that fills them, not in a follow-up.
