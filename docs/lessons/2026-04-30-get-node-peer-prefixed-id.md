# `get_node` 404'd silently on peer-prefixed `serverId:nodeId` ids

**Issue:** [krill-oss#49](https://github.com/Sautner-Studio-LLC/krill-oss/issues/49)
**Root cause category:** Skill–implementation drift (capability documented but never implemented)
**Module:** `module:krill-mcp` (`krill-mcp-service`) + `module:krill-skill`

## What happened

`list_nodes type=Peer` returns Peer entries whose `id` is the composite form `<seedServerId>:<peerNodeId>`. The `krill` skill's `references/mcp-tools.md` told agents this id was directly usable with `get_node`:

> Fetch a single node by id (UUID, **or peer-prefixed `serverId:nodeId` for cross-server peers**).

But `GetNodeTool.execute` passed the id verbatim into `KrillClient.node()`, which calls `GET https://<host>:8442/node/<id>`. The Krill server keys nodes on the bare nodeId, so the colon-prefixed string 404'd:

```
ERROR: GET https://localhost:8442/node/<srv>:<node> returned 404 Not Found: Node not found or is being deleted
```

The skill claim was aspirational from day one — `KrillRegistry` explicitly notes "Peer auto-discovery from ServerMetaData is deliberately out of scope for v1" (introduced in `71b8957 add krill claude skill (v0.0.3)`), so cross-server proxying never existed. A QA session driving the standard `list_servers` → `server_health` → `list_nodes` → `get_node` discovery flow tripped it on the very first peer.

## Fix

- `krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/KrillTools.kt` — `GetNodeTool.execute` now validates the id format **before** the HTTP call. A `:` in the id triggers an `IllegalStateException` with an actionable message: name the format, cite the tracking issue, and suggest the bare-uuid retry (which returns the local-side `KrillApp.Server` proxy node).
- `krill-mcp/skill/krill/references/mcp-tools.md` — removed the false promise on the `get_node` description; replaced with the actual limit and a pointer to the Peer entry already in `list_nodes` being the full peer-node body.
- `krill-mcp/krill-mcp-service/src/test/kotlin/krill/zone/mcp/mcp/tools/GetNodeToolTest.kt` — regression test pins the fail-fast behavior and the message contract (must name the format, cite the issue, surface the bare-uuid retry path). Bare uuids do **not** trip the check.

The `inputSchema.id.description` was also expanded to carry the same constraint — the LLM sees this in `tools/list`, so the constraint reaches the agent before any tool call.

## Why fail-fast over silent proxy

Three options were on the table per the issue:

1. Strip the `<serverId>:` prefix and route locally (cheap).
2. Resolve the prefix to the peer host and proxy (proper).
3. Reject with an actionable error.

(2) is the right long-term shape but requires net-new peer-discovery infrastructure that v1 deliberately punted on. (1) silently misleads the agent — `Peer` and `Server` nodes have different shapes, so the bare-uuid lookup returns a different *kind* of node than the agent asked for, and "404" turns into "wrong-shape body" which is harder to diagnose. (3) makes the skill–implementation contract honest in one PR, surfaces the limit at the point the agent encounters it (not just in docs an agent might not have read), and leaves room for (2) to land later by simply removing the check.

## Prevention

- **Skill claims of capability are testable contracts.** When the skill docs say "tool X accepts format Y", the tool must either accept Y or refuse it explicitly. A 404 from the underlying HTTP layer is not a contract — the LLM has no way to tell that 404 from a genuine "this node was deleted" 404. If the capability isn't there, document the limit on both the tool's `inputSchema.description` (LLM sees it pre-call) and the skill reference (LLM sees it during planning), and make the tool reject the format with the same wording.
- **`isError: true` payloads should name the retry path.** "404 Not Found" tells the agent what failed but not what to do next. `assertFailsWith` regression tests should pin the *retry guidance*, not just the failure — that's the bit that breaks silently when someone "improves" the error message.
- **When deferring a v2 capability, encode the deferral as a runtime check, not a TODO.** The `KrillRegistry` comment "Peer auto-discovery from ServerMetaData is deliberately out of scope for v1" was correct but invisible to anyone reading the skill docs. The check added here serves the same role and is impossible to drift away from — if peer support lands, the test fails and forces a deliberate rewrite of the contract.
