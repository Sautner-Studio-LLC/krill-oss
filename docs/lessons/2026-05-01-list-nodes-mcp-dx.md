# MCP tool surface DX hardening — silent unknown args, type filter, arg-name drift

**Issues:** [krill-oss#51](https://github.com/Sautner-Studio-LLC/krill-oss/issues/51) (bug, severity:medium), [#52](https://github.com/Sautner-Studio-LLC/krill-oss/issues/52) (friction), [#53](https://github.com/Sautner-Studio-LLC/krill-oss/issues/53) (skill-gap)
**Root cause category:** MCP tool DX — silent failure on input + cross-tool naming drift + missing exact-match affordance
**Module:** `module:krill-mcp` (`krill-mcp-service`) + `module:krill-skill`

## What happened

A QA discovery flow against `pi-krill-05` surfaced three drift problems on the same surface in a single session:

1. **#51 — silent unknown-arg drop.** `list_nodes parent="<id>"` returned the full unfiltered tree. There was no error and no in-band signal that `parent` was ignored — the response was indistinguishable from "no filter passed". A caller (LLM or human) inferring the arg from `record_snapshot.dataPointId` or `get_node.id` got a wrong-but-plausible answer.
2. **#52 — `record_snapshot.dataPointId` vs everything-else's `id`.** The skill's `record_snapshot` walkthrough is the most prominent place a first-time agent encounters a DataPoint id, and the variable name there was `dataPointId`. Carrying it directly into the very next paragraph's `read_series` verification call hit `ERROR: Missing required argument: id`. Two adjacent tools, same node, different arg name.
3. **#53 — `type` is a substring match with no exact-match option.** `list_nodes type="DataPoint"` returned 15 nodes (10 `KrillApp.DataPoint` + 5 `KrillApp.DataPoint.Graph`). `type="DataPoin"` (typo) returned the same 15. The substring behavior was documented in the tool's `inputSchema.description` but not in the skill prose, and the LLM walks the prose first.

All three came from the same QA session because they're the same family of failure: the MCP layer is permissive on input in ways that surprise a first-time caller and have no narration in the skill.

## Fix

- **`krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/McpServer.kt`** — `toolsCall` now validates `arguments.keys` against `tool.inputSchema.properties.keys` before invoking `tool.execute`. Unknown args produce a structured `isError: true` with text `"Unknown argument(s) for <tool>: <sorted, comma-joined>. Allowed: <sorted, comma-joined>."`. One central enforcement point — every tool gets the rejection for free, and adding a new tool can't accidentally regress.
- **`krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/KrillTools.kt`** — `ListNodesTool` learns a `typeExact: boolean` schema field. The filter logic is extracted into `ListNodesTool.Companion.filterByType(JsonArray, String?, Boolean)` so it's testable without HTTP. Exact-match semantics: suffix-after-dot — the node's FQN must equal `typeFilter` directly OR end with `".$typeFilter"`. That accepts `"DataPoint"`, `"KrillApp.DataPoint"`, and the full FQN consistently, while excluding `"DataPoint.Graph"`.
- **`krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/NodeTools.kt`** — `RecordSnapshotTool` argument renamed `dataPointId` → `id`. Schema property, `required` array, `execute()` extraction, error messages, and response field all updated. No back-compat alias — McpServer's reject-unknown path means any stale `dataPointId` call now returns a clear error instead of silently working.
- **`krill-mcp/skill/krill/SKILL.md`** + **`references/mcp-tools.md`** — `record_snapshot` examples switched to `id`; `list_nodes` documentation calls out the substring/`typeExact` pair plus the new unknown-arg rejection so an agent reading the skill knows the surface contract.
- **Tests** — `McpServerTest` (4 cases) covers the reject path, multi-arg sorting, the happy path, and the no-properties-tool edge. `ListNodesToolTest` (7 cases) covers null/substring/exact for bare-leaf, short, and full-FQN inputs plus a malformed-node edge. `RecordSnapshotToolTest` (2 cases) pins the schema-side rename.

## Why central rejection over per-tool guards

`GetNodeTool.execute` already does its own input validation (peer-prefixed id format, krill-oss#49). I considered putting the unknown-arg check there as a per-tool helper. Rejected:

- New tools added later would need to remember the call. The whole class of bug is "X was silently accepted because the author forgot to validate" — solving it once at the dispatcher makes the forgetting impossible.
- The validation is identical across tools (compare arg keys to schema keys). Repeating it in every `execute` is a copy-paste hazard.
- The error message is identical across tools, modulo the tool name. Centralizing means callers see a uniform shape no matter which tool they hit.

The McpServer location pays a small cost: the tool's `execute` now starts with `arguments` already validated, so per-tool checks like `error("Missing required argument: id")` only run after the unknown-arg gate. That's fine — the check there now exists for the *missing*-required-arg case, not the *unknown*-arg case. Both errors still surface the same way (`isError: true` with descriptive text); they're just produced at different layers.

## Why no `dataPointId` back-compat alias

The QA report (#52) suggested keeping `dataPointId` as an alias for one minor "with a deprecation note in the description". I declined because:

- McpServer's reject-unknown means a stale `dataPointId` call now produces a clear, agent-readable error (`Unknown argument(s) for record_snapshot: dataPointId. Allowed: id, server, snapshots, timestamp, value.`) — not a 404, not a silent miss. The cost of the breakage is one re-call.
- There are no production consumers of `record_snapshot` outside this repo's QA surface and the bundled skill. Both update in this PR.
- `Avoid backwards-compatibility hacks` (CLAUDE.md). An alias for one undocumented downstream caller is the kind of cruft that's hard to delete later.

If a real external consumer surfaces, the right shape is to add `dataPointId` back as an alias **inside `RecordSnapshotTool.execute`** (read whichever is present, prefer `id`) — with the schema still declaring only `id` so unknown-arg validation continues to surface every other typo. That's a clean future change; a "soft alias" today would just mute the signal.

## Prevention

- **Silent-drop on unknown input is a category of bug, not a per-tool one.** Validate at the dispatcher. Whenever you see "this layer accepts arbitrary keys and the schema is the contract", check whether the schema is *enforced* — most often the answer is no, and the fix is one centralized check away.
- **Adjacent-tool arg-name drift hides until a chained call hits it.** When two tools operate on the same kind of node, the arg name should match unless there's a specific reason it can't (`get_node` is generic over any node, `record_snapshot` is DataPoint-only — but both target a single existing node by UUID, so both should use `id`). A lint rule could enforce this if drift becomes recurrent: assert that arguments named `*Id` have a matching plain-`id` cousin elsewhere or document why not.
- **Substring filters with no exact-match option are a footgun.** They're convenient ("DataPoint" matches everything DataPoint-like) but have no end-of-string anchor in the skill prose, so an agent treating "DataPoint" as a type *name* gets subtype rows it didn't ask for. Either default to exact and offer substring as opt-in, or — as here — keep substring as default but ship the exact-match flag *and* document it in the skill, not just the schema. Schema descriptions alone don't reach the LLM during planning; only the skill prose does.
- **Bundle related-surface PRs.** Three tightly interlocking fixes to the same file with shared tests and one QA verification round = one PR, not three. CLAUDE.md's `fix/<issue-number>-<slug>` convention is a default; deviate when the fixes interact (here #51's reject-unknown is the safety net that makes #52's unaliased rename safe).
