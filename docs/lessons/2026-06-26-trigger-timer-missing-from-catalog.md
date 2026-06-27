# KrillApp.Trigger.Timer missing from MCP node-type catalog

**Issue:** [krill-oss#172](https://github.com/Sautner-Studio-LLC/krill-oss/issues/172)
**Root cause category:** Missing catalog entry — server ships the type, MCP never registered it
**Module:** `krill-mcp`

## What happened

The krill server (1.0.1268) ships `KrillApp.Trigger.Timer` (ServerTimerProcessor /
TimerBoss) — a one-shot countdown trigger where EXECUTE starts the countdown and
RESET cancels it. `KrillNodeTypes.kt` had no entry for it, so `list_node_types`
never returned it and `create_node` rejected the type with "Unknown node type".

As a by-product, `KrillApp.Trigger.SilentAlarmMs` (removed in #170) was still
referenced as a valid child type in the `KrillApp.Trigger` container entry and
in `KrillApp.DataPoint`'s `validChildTypes`, and as a valid parent in
`KrillApp.Executor`'s `validParentTypes`. These three stale references were
cleaned up in the same PR.

## Fix

- `KrillNodeTypes.kt`:
  - Added import for `krill.zone.shared.krillapp.trigger.timer.TimerMetaData`.
  - Added `KrillApp.Trigger.Timer` entry with correct `typeFqn`, `metaFqn`,
    `defaultMeta` (serialized from `TimerMetaData(name = "Timer")` via `sdkMeta`),
    and documentation of the `delay` field and EXECUTE/RESET semantics.
  - Added `KrillApp.Trigger.Timer` to `validChildTypes` of `KrillApp.Trigger`
    container and `KrillApp.DataPoint`.
  - Added `KrillApp.Trigger.Timer` to `validParentTypes` of `KrillApp.Executor`.
  - Replaced stale `KrillApp.Trigger.SilentAlarmMs` references in the three
    parent/child hint lists with `KrillApp.Trigger.Timer`.
- `skill/krill/references/mcp-tools.md`:
  - Added `KrillApp.Trigger.Timer` row to the `update_node` examples table.
  - Removed stale `SilentAlarmMs` mention from the `RESET` verb description.
- `skill/krill/SKILL.md`:
  - Added a note describing when to use Timer vs CronTimer.
- `CreateNodeToolTest.kt`: four new guard tests confirm the type resolves by
  short name, has the correct `metaFqn`, has the correct `defaultMeta` type
  discriminator, and includes the `delay` field.

## Prevention

- When removing a type from the catalog (as in #170), also grep `validChildTypes`
  and `validParentTypes` in every other entry for the removed short name and
  remove those references in the same PR.
- When the krill server ships a new `KrillApp.*` subtype, add an entry to
  `KrillNodeTypes.kt` + skill docs in a single PR so the catalog never lags
  the server.
- The `KrillApp.kt` sealed class in `krill-sdk` is the source of truth for all
  registered types — `grep "data object"` there after any krill server update
  and cross-check against `KrillNodeTypes.TYPE_TABLE`.
