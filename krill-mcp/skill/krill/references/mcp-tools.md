# krill-mcp tools

The krill-mcp server is a Ktor daemon (default port `50052`) that bridges Claude to a Krill swarm via the Model Context Protocol. Auth is a single `Authorization: Bearer <token>` header — the token is the cluster's PIN-derived key (HMAC-SHA256 of the 4-digit PIN with key `krill-api-pbkdf2-v1`, hex-encoded).

When invoked through Claude Code or Claude Desktop, the tools below appear as MCP tools — call them by name, no manual JSON-RPC needed.

## Setup check

Before calling any tool, confirm the user has a Custom Connector configured:
- **URL:** `http://<krill-mcp-host>:50052/mcp` (typically the same Pi running the Krill server)
- **Header:** `Authorization: Bearer <token>` — printed by `sudo krill-mcp-token` on the host

If no MCP connector is registered in the current Claude session, fall back to raw `curl` against the endpoint and explain the setup so the user can wire it once.

## Tools (v0.0.3 — all read-only)

### `list_servers`
Returns every Krill server the local krill-mcp instance has registered. Use first to discover swarm topology.
```json
{"name": "list_servers", "arguments": {}}
```
Response shape: `{"servers": [{"id": "<uuid>", "host": "<host>:<port>"}, ...]}`

### `list_nodes`
Lists all nodes on a given server. Without `server`, defaults to the first registered server. Optional `type` substring filter (case-insensitive) — e.g. `"DataPoint"`, `"Trigger"`, `"Pin"`.
```json
{"name": "list_nodes", "arguments": {"server": "<id|host|host:port>", "type": "DataPoint"}}
```
Response shape: `{"server": "<id>", "count": N, "nodes": [<full node tree, each with id/parent/host/type/state/meta/timestamp>, ...]}`

Each node's `type.type` is the dotted KrillApp type (e.g. `krill.zone.shared.KrillApp.DataPoint`) — strip the `krill.zone.shared.` prefix to look up that node type in `node-types/INDEX.md`.

### `get_node`
Fetch a single node by id (UUID, or peer-prefixed `serverId:nodeId` for cross-server peers).
```json
{"name": "get_node", "arguments": {"server": "<optional>", "id": "<uuid>"}}
```

### `read_series`
Read time-series data for a `KrillApp.DataPoint` node. Defaults to the last hour; pass `startMs` / `endMs` (ms since epoch) to override. Returns an array of `{timestamp, value}` snapshots.
```json
{"name": "read_series", "arguments": {"id": "<datapoint-uuid>", "startMs": 1776700000000, "endMs": 1776720000000}}
```

### `server_health`
Returns the `/health` payload of a server: server node id, name, platform, peer list, and child node ids. Lighter than `list_nodes` for a quick "is anything reachable" check.
```json
{"name": "server_health", "arguments": {"server": "<optional>"}}
```

## Standard discovery flow

For most user requests, call in this order and stop as soon as you have enough:
1. `list_servers` — what's reachable
2. `server_health` — sanity check + peer awareness
3. `list_nodes` (optionally with `type` filter) — full node inventory
4. `get_node` for any specific node the user references by name
5. `read_series` only when the user wants historical values, a graph, or a calculation

## What's NOT here yet

No write tools in v0.0.3. You **cannot** create, update, delete, or execute a node from MCP. If the user asks to "set up an alarm" or "create a diagram", emit the configuration as text/SVG/JSON and instruct them to apply it manually in the Krill app — then note that this would become end-to-end automatic once write tools land.
