# node-action-verb: krill-mcp surface (krill-oss#82)

## What happened

The `NodeAction` enum (`EXECUTE` / `RESET`) and the `ActionNodeMetaData` interface were added to `krill-sdk` (v0.0.23, krill-oss#80), and the dispatch logic was wired into the upstream `krill` server. The MCP surface — which agents use to configure and inspect nodes — had no way to read or set a node's action verb: `defaultMeta` skeletons in `KrillNodeTypes` omitted `nodeAction`, and there was no dedicated tool for updating an existing node's verb. Agents could work around this by passing `meta: {nodeAction: "RESET"}` to `create_node`, but the field was invisible in `list_node_types` output and `set_node_action` didn't exist for updating live nodes.

## Fix

- **`krill-mcp/krill-mcp-service/…/krill/KrillNodeTypes.kt`** — added `nodeAction: "EXECUTE"` to `defaultMeta` (and `NODE_ACTION_HINT` to `metaFieldHints`) for every `ActionNodeMetaData` type: all Trigger variants (Button, HighThreshold, LowThreshold, SilentAlarmMs, CronTimer, Color, IncomingWebHook, base Trigger), all Executor variants (LogicGate, OutgoingWebHook, Lambda, Calculation, Compute, SMTP, MQTT, SerialDevice), DataPoint.Graph, and TaskList.
- **`krill-mcp/krill-mcp-service/…/mcp/tools/NodeTools.kt`** — added `SetNodeActionTool`: validates `action ∈ {EXECUTE, RESET}` before touching the network, rejects known non-action types by checking if `nodeAction` is in their `defaultMeta`, patches `meta.nodeAction`, and POSTs with `state=USER_EDIT`.
- **`krill-mcp/krill-mcp-service/…/Main.kt`** — registered `SetNodeActionTool` in the tool list.
- **`skill/krill/references/mcp-tools.md`** — documented `set_node_action` alongside existing generic node write tools.
- **`skill/krill/SKILL.md`** — updated the write-surface summary bullet to mention `set_node_action`.

## Prevention

- When a new field is added to a MetaData interface across the SDK, the corresponding `defaultMeta` entry in `KrillNodeTypes` must be updated in the same krill-mcp PR — the registry is the agent's source of truth for what fields exist. If the update is missed, `list_node_types` presents an incomplete skeleton and agents writing `meta` overlays have no signal that the field exists.
- Every new MCP tool must be documented in both `references/mcp-tools.md` and the `SKILL.md` write-surface summary; the skill and the tool list go out of sync otherwise.
- The action validation guard (`action !in VALID_ACTIONS`) must fire before the server resolution call — test it with an invalid value and an empty registry to confirm the error is about the enum, not the missing server.
