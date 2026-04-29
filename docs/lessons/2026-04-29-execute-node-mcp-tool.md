# `execute_node` MCP tool — manual fire primitive

**Issue:** [krill-oss#24](https://github.com/bsautner/krill-oss/issues/24)
**Root cause category:** Missing primitive — wrong-abstraction workaround in active use
**Module:** `krill-mcp`

## What happened

A QA Claude Desktop session tried to toggle a `KrillApp.Server.Pin` named
"Vivarium Mister" by firing its child `KrillApp.Executor.LogicGate`. The MCP
write surface in v0.0.8 (`create_node`, `record_snapshot`, `delete_node`,
plus the Diagram helpers) had no way to make a node fire *now*. The only
workaround was `record_snapshot` on whatever DataPoint sat upstream of the
gate, with a value chosen to flip the gate's evaluation — indirect, history-
polluting, and unworkable when the gate's source was a derived/computed
value with no DataPoint to write to.

The Compose client itself already has a manual-execute button. Reading the
upstream `ClientNodeManager.execute(node)` revealed the wire pattern: it
just POSTs the node body back to `/node/{id}` with `state="EXECUTED"`. The
server's `update()` runs `node.type.emit(node)` on every upsert, and the
per-type processor (`ServerExecutorProcessor`, `LogicGateProcessor`, etc.)
reacts to the `EXECUTED` state and runs the action. So the missing piece
was an MCP-layer wrapper, not a new server endpoint.

## Fix

1. New `ExecuteNodeTool` in `krill-mcp-service/.../mcp/tools/KrillTools.kt`.
   Fetches the node, refuses pure-container / infrastructure types
   (`KrillApp.Server`, `KrillApp.Client`, `KrillApp.Client.About`,
   `KrillApp.Server.Peer`, `KrillApp.Server.Backup`), POSTs the body back
   with `state="EXECUTED"` and a fresh `timestamp`, and returns a post-fire
   `get_node` round-trip so callers can verify without a separate read.
2. Block-list rather than allow-list: unknown FQNs default to firable so
   new node types ship without a coordinated MCP release.
3. Registered alongside the other read tools in `Main.kt`'s `tools = listOf(...)`.
4. Skill docs updated — `skill/krill/SKILL.md` adds a "fire this trigger /
   executor once" workflow and lists the new tool in the bundled-references
   index; `skill/krill/references/mcp-tools.md` documents the wire shape and
   adds an entry to the "No REST equivalent on `:8442`" matrix.
5. Regression test (`ExecuteNodeToolTest`) covers the firable / not-firable
   type partition and pins the tool name and `id`-required input schema.

## Prevention

- **Read what the existing client does over the wire before assuming a new
  server endpoint is needed.** The upstream Compose app's
  `ClientNodeManager.execute()` was the design source for this tool — its
  approach (POST with `state=EXECUTED`) is what the server's `update()`
  already supports. Adding `/execute` REST routes would have been
  duplicative and required an upstream change.
- **Don't redirect callers to a workaround when the right primitive is
  missing.** Documenting the `record_snapshot`-upstream pattern looked
  attractive as a stopgap, but it's structurally wrong: it pollutes the
  DataPoint's time-series history, only works when the chain happens to
  read from a DataPoint, and confuses agents about what `record_snapshot`
  is for. Adding the missing primitive is cheaper than teaching every
  caller a workaround.
- **Surface the type-policy split (firable vs. not-firable) in the tool
  description, not just the error message.** Agents pick tools by reading
  descriptions before they call anything; a description that names both
  the supported types (Triggers, Executors) and the rejected ones
  (Server, Client) saves a wasted call.
- **Keep the skill docs in lockstep with new tools.** The companion
  skill is the agent-facing surface — a tool no one knows about is the
  same as no tool. `skill/krill/references/mcp-tools.md` and
  `skill/krill/SKILL.md` must be updated in the same PR (per
  `krill-mcp/CLAUDE.md`).
