# krill-mcp tools

The krill-mcp server is a Ktor daemon (default port `50052`) that bridges Claude to a Krill swarm via the Model Context Protocol. Auth is a single `Authorization: Bearer <token>` header — the token is the cluster's PIN-derived key (HMAC-SHA256 of the 4-digit PIN with key `krill-api-pbkdf2-v1`, hex-encoded).

When invoked through Claude Code or Claude Desktop, the tools below appear as MCP tools — call them by name, no manual JSON-RPC needed.

## Setup check

Before calling any tool, confirm the user has a Custom Connector configured:
- **URL:** `http://<krill-mcp-host>:50052/mcp` (typically the same Pi running the Krill server)
- **Header:** `Authorization: Bearer <token>` — printed by `sudo krill-mcp-token` on the host

If no MCP connector is registered in the current Claude session, fall back to raw `curl` against the endpoint and explain the setup so the user can wire it once.

## Read tools (v0.0.4)

### `list_servers`
Returns every Krill server the local krill-mcp instance has registered. Use first to discover swarm topology.
```json
{"name": "list_servers", "arguments": {}}
```
Response shape: `{"servers": [{"id": "<uuid>", "baseUrl": "https://host:port"}, ...]}`

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

## Project + Diagram write tools (v0.0.4)

These are the only write tools today. All other node types (DataPoints, Triggers, Executors, Filters, Pins, etc.) are still read-only.

### `list_projects`
List existing `KrillApp.Project` containers on a server. Call this before `create_diagram` to pick a parent.
```json
{"name": "list_projects", "arguments": {"server": "<optional>"}}
```
Response: `{"server": "<id>", "count": N, "projects": [{"id": "<uuid>", "name": "...", "description": "..."}, ...]}`

### `create_project`
Create a new `KrillApp.Project` node — an organizational container for Diagrams, TaskLists, Journals, Cameras. Returns the new project id.
```json
{"name": "create_project", "arguments": {"server": "<optional>", "name": "Aquarium monitoring", "description": "Tank 1 sensors + dashboard"}}
```
Response: `{"server": "<id>", "projectId": "<new-uuid>", "name": "...", "message": "..."}`

### `create_diagram`
Create a `KrillApp.Project.Diagram` node under an existing project. `source` is the SVG content (inline); `anchorBindings` maps SVG anchor ids (strings starting with `k_`) to the target node UUIDs the renderer should bind them to. Optional `uploadFileName` also PUTs the raw SVG to `/project/{projectId}/diagram/{file}` so it's available at a stable URL.
```json
{
  "name": "create_diagram",
  "arguments": {
    "server": "<optional>",
    "projectId": "<project-uuid>",
    "name": "Tank 1 dashboard",
    "description": "Live level + pump state",
    "source": "<svg xmlns=\"http://www.w3.org/2000/svg\" ...> ... </svg>",
    "anchorBindings": {
      "k_tank_level": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
      "k_pump_relay": "65691be2-f712-4727-8125-c94b66b3820e"
    },
    "uploadFileName": "tank1.svg"
  }
}
```
Response: `{"server": "<id>", "projectId": "<id>", "diagramId": "<new-uuid>", "anchorCount": N, "fileUploaded": bool}`

**Precondition:** the `projectId` must exist. Call `list_projects` first, or create a project with `create_project` and reuse the returned id.

### `update_diagram`
Update an existing diagram. All meta fields are optional — omitted fields keep their current value. Use this for "improve this diagram" workflows: call `get_diagram` to read the current SVG, reason about changes, then call `update_diagram` with the replacement `source` and/or `anchorBindings`.
```json
{
  "name": "update_diagram",
  "arguments": {
    "diagramId": "<uuid>",
    "source": "<svg>...updated markup...</svg>",
    "anchorBindings": {"k_tank_level": "...", "k_ammonia_ppm": "..."},
    "uploadFileName": "tank1.svg"
  }
}
```
Response: `{"server": "<id>", "diagramId": "<id>", "updated": ["source","anchorBindings", ...], "fileUploaded": bool}`

### `get_diagram`
Fetch a Diagram node's SVG source + anchor bindings — the input you need to propose improvements.
```json
{"name": "get_diagram", "arguments": {"diagramId": "<uuid>"}}
```
Response: `{"server": "<id>", "diagramId": "<id>", "projectId": "<id>", "name": "...", "description": "...", "source": "<svg>...</svg>", "anchorBindings": {...}}`

### `upload_diagram_file`
Stash a raw SVG file at `/project/{id}/diagram/{file}` without touching the node graph. Useful when preparing assets for a diagram you'll create later, or for static embeds.
```json
{"name": "upload_diagram_file", "arguments": {"projectId": "<uuid>", "fileName": "floorplan.svg", "source": "<svg>...</svg>"}}
```

### `download_diagram_file`
Download a raw SVG previously uploaded to `/project/{id}/diagram/{file}`.
```json
{"name": "download_diagram_file", "arguments": {"projectId": "<uuid>", "fileName": "floorplan.svg"}}
```

## Standard discovery flow

For most user requests, call in this order and stop as soon as you have enough:
1. `list_servers` — what's reachable
2. `server_health` — sanity check + peer awareness
3. `list_nodes` (optionally with `type` filter) — full node inventory
4. `get_node` for any specific node the user references by name
5. `read_series` only when the user wants historical values, a graph, or a calculation

## Standard diagram flow

1. `list_nodes` with `type=DataPoint` — know what you can bind.
2. `list_projects` — pick a parent. If none, call `create_project`.
3. Author SVG with `k_*` anchors, build the bindings map.
4. `create_diagram` (or `update_diagram` for edits of existing ones).
5. Confirm with `get_diagram` if the user wants a round-trip sanity check.

## What's NOT here yet

No write tools for DataPoints, Triggers, Filters, Executors, Pins, TaskLists, Journals, or Cameras. If the user asks to "set up an alarm", emit the configuration as JSON and instruct them to apply it manually in the Krill app — then note that broader write support is a future capability.
