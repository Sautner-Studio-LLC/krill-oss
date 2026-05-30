---
date: 2026-05-30
slug: mcp-invocation-verbs
issues: ["krill-oss#107"]
modules: ["krill-mcp", "krill-sdk"]
---

## What happened

The openspec phases `add-node-action-verb`, `unify-source-verb-wiring`, and `separate-crud-from-invocation` landed in the Krill server and krill-sdk (0.0.24 → 0.0.36) but left krill-mcp stranded on stale field names and missing capabilities.

Three concrete breakages:

1. **`executionSource` → `invocationTriggers` rename.** Every call to `set_node_wiring` that passed an `executionSource` array was silently dropped by the server (`ignoreUnknownKeys = true`), leaving nodes un-wired with no error visible to the caller. `KrillNodeTypes.kt` also advertised the stale name in every `defaultMeta` skeleton, so `list_node_types` gave MCP agents a wrong field name to use.

2. **`SOURCE_VALUE_MODIFIED` / `PARENT_EXECUTE_SUCCESS` → `SOURCE_INVOKED`.** The old enum values no longer exist on the server. Validation in `SetNodeWiringTool` accepted them, and the MCP silently posted values the server rejects.

3. **No `invoke_node` tool.** The new `POST /node/{id}/invoke` route (`ServerNodeManager.invoke → processor.onInvoke`) provides the only correct path to trigger a RESET verb end-to-end. Without an MCP tool wrapping it, QA could not verify RESET behavior through the swarm at all.

## Fix

- `krill-mcp/gradle/libs.versions.toml`: bumped `krill-sdk` from `0.0.24` to `0.0.36`.
- `KrillNodeTypes.kt`: replaced all `"executionSource"` keys with `"invocationTriggers"` and updated the enum hint string from `"List<enum: PARENT_EXECUTE_SUCCESS | SOURCE_VALUE_MODIFIED | ON_CLICK>"` to `"List<enum: SOURCE_INVOKED | ON_CLICK>"`.
- `NodeTools.kt` / `SetNodeWiringTool`: renamed the parameter `executionSource` → `invocationTriggers` in the `inputSchema`, `execute()` body, and companion object (`VALID_EXECUTION_SOURCES` → `VALID_INVOCATION_TRIGGERS` with values `{"SOURCE_INVOKED", "ON_CLICK"}`). Also fixed a stale description reference in `SetNodeActionTool`.
- `KrillClient.kt`: added `invokeNode(id, byNodeId, byHostId, verb)` posting to `/node/{id}/invoke`.
- `NodeTools.kt` / `InvokeNodeTool`: new tool wrapping the invoke route, with identity pair validation and verb validation before any HTTP call.
- `Main.kt`: registered `InvokeNodeTool(registry)` in the tools list.
- `SetNodeWiringToolTest.kt`: updated all references from stale field/constant names; added a regression test asserting `SOURCE_VALUE_MODIFIED` is rejected.
- `InvokeNodeToolTest.kt`: new test file covering schema and pre-HTTP validation.
- `skill/krill/references/mcp-tools.md`: updated `set_node_wiring` table, example calls, `set_node_action` cross-reference, and added `invoke_node` documentation.
- `skill/krill/SKILL.md`: updated the `references/mcp-tools.md` pointer to enumerate all write tools including `invoke_node`.

## Prevention

- When openspec phases rename SDK fields, file a krill-mcp tracking issue at the same time so the rename doesn't sit stale across a version bump.
- `KrillNodeTypes.kt` `defaultMeta` skeletons are plain string maps with no compile-time tie to the SDK enum names. A companion unit test asserting the exact allowed-values set against the SDK's `InvocationTrigger` enum (or at minimum a string-based cross-check) would have caught the drift at build time.
- The valid-values set in `SetNodeWiringTool.VALID_INVOCATION_TRIGGERS` should be derived from or cross-checked against the SDK enum rather than being a hand-maintained copy.
- New Krill server routes need a corresponding MCP tool filed as a follow-up issue at merge time — `POST /node/{id}/invoke` shipped with no MCP wrapper for several SDK versions.
