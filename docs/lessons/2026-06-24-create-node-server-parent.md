## What happened

`create_node` failed whenever the caller passed the server's own UUID as the `parent` argument. The tool description explicitly told callers to do this for top-level nodes ("for top-level server children … pass the server id"), but the implementation then called `GET /node/{parent-id}` to verify the parent exists. The Krill server does not expose the server entity via that endpoint — it is not a node in the `/node/` namespace — so the request failed and the tool returned an error:

```
create_node: ERROR: Parent node '<server-id>' not found on server <server-id>.
```

The kraken demo pipeline's `add_cron` scene triggered this by creating a `KrillApp.Trigger.CronTimer` at the server root. The `parent` field was also marked `required` in the JSON schema, making it impossible to create root-level nodes without knowing the server id in advance.

## Fix

- `krill-mcp/krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/NodeTools.kt`:
  - Made `parent` **optional** in `inputSchema` — removed it from the `required` array and updated the description to say it defaults to the server root.
  - In `CreateNodeTool.execute()`, default `parentId` to `client.serverId` when the caller omits `parent`.
  - Added a guard: when `parentId == client.serverId`, skip the `GET /node/{id}` HTTP call and synthesize a minimal server-typed parent node (`type.type = "krill.zone.shared.KrillApp.Server"`) so downstream parent-type validation can still run without making a network call.
  - Added `serverParentNode(serverId)` as an `internal` helper (testable without HTTP).
- `krill-mcp/krill-mcp-service/src/test/kotlin/krill/zone/mcp/mcp/tools/CreateNodeToolTest.kt`:
  - Added three regression tests for `serverParentNode`: verifies the FQN, embedded server id, and that `derivedDefaultName` doesn't throw on the synthesized node.
- `krill-mcp/skill/krill/references/mcp-tools.md`:
  - Updated `create_node` docs to reflect that `parent` is optional and to show root-node examples without `parent`.

## Prevention

- Tool descriptions and JSON schemas must agree: if the description tells callers to pass a value, the implementation must handle that value — or the schema must not allow it.
- When a parent-existence check hits an entity that is conceptually the root (a server, a registry, a scope), guard it explicitly rather than letting the HTTP call fail. The server root is always reachable via `client.serverId`; treat it as a well-known bypass case.
- Required fields in MCP tool schemas have a high friction cost — if a field has a sensible default, make it optional and document the default.
