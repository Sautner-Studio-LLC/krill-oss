# unify-source-verb-wiring: krill-mcp surface (krill-oss#87)

## What happened

The `unify-source-verb-wiring` change (krill-oss openspec) made every MetaData type implement `TargetingNodeMetaData`, meaning `sources`, `targets`, `executionSource`, and `nodeAction` are now universal — not just on Triggers and Executors. `DataPointMetaData` and `PinMetaData` gained these fields in krill-sdk v0.0.24. The upstream `krill` server was already wired to dispatch source-owned verbs via `executeSources()`. The MCP surface was the final piece: agents had no tool to set or read wiring fields on arbitrary node types. `set_node_action` existed but had a type restriction (checked `nodeAction !in spec.defaultMeta`), which rejected DataPoint and Pin. `KrillNodeTypes` defaultMeta for DataPoint and Pin lacked sources/targets/executionSource/nodeAction, so `list_node_types` presented an incomplete skeleton.

## Fix

- **`krill-mcp/krill-mcp-service/…/mcp/tools/NodeTools.kt`** — added `SetNodeWiringTool`: accepts `sources`, `targets`, `executionSource`, and `nodeAction` (all optional; at least one required), validates enum values before touching the network, fetches the existing node, shallow-merges the updates into `meta`, and POSTs with `state=USER_EDIT`. Works on any node type without type restriction. Also removed the type restriction from `SetNodeActionTool` (the `"nodeAction" !in spec.defaultMeta` guard) since all types now carry it.
- **`krill-mcp/krill-mcp-service/…/Main.kt`** — registered `SetNodeWiringTool`.
- **`krill-mcp/krill-mcp-service/…/krill/KrillNodeTypes.kt`** — added `sources`, `targets`, `executionSource`, and `nodeAction` to `defaultMeta` (and matching `metaFieldHints`) for `KrillApp.DataPoint` and `KrillApp.Server.Pin`.
- **`krill-mcp/gradle/libs.versions.toml`** — bumped `krill-sdk` dependency from `0.0.16` to `0.0.24` (first version with universal `TargetingNodeMetaData`).
- **`krill-mcp/krill-mcp-service/build.gradle.kts`, `Main.kt`, `package/DEBIAN/control`, `skill/krill/SKILL.md`, `skill/krill/references/mcp-tools.md`** — five-site version sync to `0.0.10`.
- **`skill/krill/references/mcp-tools.md`** — documented `set_node_wiring`, updated `set_node_action` to note it now applies universally, added end-to-end Button→TaskList wiring example.
- **`skill/krill/SKILL.md`** — updated write-surface summary bullet for v0.0.10.

## Prevention

- When a new MetaData interface is made universal in krill-sdk, the krill-mcp PR must update `KrillNodeTypes` defaultMeta for every affected type in the same change — the registry is the agent's source of truth.
- Type restrictions in MCP tools that gate on `KrillNodeTypes.defaultMeta` presence become stale when the SDK widens a contract. Guard condition reviews should be part of the krill-mcp checklist whenever krill-sdk's MetaData interfaces change.
- The krill-sdk dep in `krill-mcp/gradle/libs.versions.toml` must track the latest published Maven Central version — letting it drift leaves the MCP unable to resolve new features even when the SDK ships them.
- All five version sites must be synced atomically in the same PR to avoid misleading `SERVER_VERSION` headers or broken Debian package installs.
