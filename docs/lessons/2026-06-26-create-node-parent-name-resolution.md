# create_node failed when parent was a display name instead of a UUID

**Issue:** [krill-oss#168](https://github.com/Sautner-Studio-LLC/krill-oss/issues/168)
**Root cause category:** Missing capability — tool accepted only UUIDs for `parent`
**Module:** `krill-mcp`

## What happened

The kraken demo pipeline (`scripts/demo/digital-logic`, scene `add_xor`) called
`create_node` with `parent: 'B'` — the human-readable `meta.name` of an existing
node. The tool only accepted a node UUID or the server id as the `parent`
argument. When the value was a plain name, the tool tried `GET /node/B` (or
whatever name string was passed), which returned a non-2xx response; the
`runCatching` swallowed the error and the null result triggered a misleading
"Parent node not found" error citing the parent string verbatim.

## Fix

- `NodeTools.kt` (`CreateNodeTool`):
  - Replace the single `if (parentId == client.serverId)` branch with a `when`
    block covering three cases: absent/server-id → synthesize stub; UUID →
    direct `GET /node/{id}` (existing behaviour); anything else → call
    `client.nodes()` and resolve by case-insensitive `meta.name` match.
  - Add `internal fun isUuid(s: String): Boolean` and
    `internal fun resolveNodeByName(nodes: JsonArray, name: String): JsonObject?`
    as testable pure helpers.
  - Update the `parent` field description in `inputSchema` to document name
    acceptance.
- `skill/krill/references/mcp-tools.md`: update `create_node` docs to mention
  that `parent` accepts either a UUID or a display name.
- `CreateNodeToolTest.kt`: six new unit tests cover `isUuid` (true/false cases)
  and `resolveNodeByName` (found, not found, empty list, first-match-wins with
  duplicates).

## Prevention

- Demo pipelines and agent scripts should prefer UUIDs (returned by
  `create_node`) over display names — name-based resolution adds one extra
  network call and silently picks the first match when names are not unique.
- Whenever a tool accepts a "selector" parameter (node id, server id, etc.),
  consider whether human-readable names are a realistic input and test that path
  explicitly. The demo pipeline is the integration test for this surface.
