# krill-mcp tools

The krill-mcp server is a Ktor daemon (default port `50052`) that bridges Claude to a Krill swarm via the Model Context Protocol. Auth is a single `Authorization: Bearer <token>` header — the token is the cluster's PIN-derived key (HMAC-SHA256 of the 4-digit PIN with key `krill-api-pbkdf2-v1`, hex-encoded).

When invoked through Claude Code or Claude Desktop, the tools below appear as MCP tools — call them by name, no manual JSON-RPC needed.

## Topology — two ports, two roles

A Krill deployment exposes **two** separate HTTP services. Don't conflate them:

| Service              | Port  | Scheme | Cert         | Path prefix        | Who talks to it                                |
|----------------------|-------|--------|--------------|--------------------|------------------------------------------------|
| `krill-mcp` daemon   | 50052 | `http` | none         | `/mcp`, `/healthz` | Claude (as an MCP Custom Connector)            |
| `krill` server       | 8442  | `https`| self-signed  | `/nodes`, `/node/{id}`, `/health`, `/trust`, ... | `krill-mcp` (fans out), and Krill client apps  |

Both accept the **same** `Authorization: Bearer <token>` — the PIN-derived key. Krill itself trusts any self-signed cert, so when you fall back to hitting `krill` directly you need `curl -k` (or equivalent).

## Setup check — connector + bearer

Before calling any MCP tool, confirm the user has a Custom Connector configured:
- **URL:** `http://<krill-mcp-host>:50052/mcp` — plain HTTP on the MCP daemon (typically the same Pi running `krill`)
- **Header:** `Authorization: Bearer <token>` — the PIN-derived hex string

### Finding the bearer token

In order of convenience:
1. **On the krill-mcp host**, run `sudo krill-mcp-token` — prints the connector URL and bearer ready to paste.
2. **On any machine where `krill` was installed**, the encoded PIN lives at `~/.krill/pin_token` (64-char hex). Use it as-is: `Authorization: Bearer $(cat ~/.krill/pin_token)`. If the user tells you they have Krill installed locally, check here before asking them to SSH anywhere.
3. **On a krill / krill-mcp server directly**, the credential file is `/etc/krill/credentials/pin_derived_key` (mode 0400, owned by `krill:krill`) or `/etc/krill-mcp/credentials/pin_derived_key` (a symlink to the former when co-installed).

If no MCP connector is registered in the current Claude session, fall back to raw `curl` against `http://<host>:50052/mcp` with the bearer — see the direct-to-krill fallback at the end of this file for a second-level workaround.

## Read tools (v0.0.5)

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

## Project + Diagram write tools (v0.0.5)

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
End-to-end: uploads the SVG to `/project/{projectId}/diagram/{fileName}`, then creates a `KrillApp.Project.Diagram` node whose `meta.source` is the public URL of that file. **`DiagramMetaData.source` is a URL, not inline markup** — passing SVG markup to the node directly would produce a broken diagram.

- `svg` — the SVG content (required). Must contain a `<svg>` tag. The server enforces a 2 MB cap.
- `fileName` — optional. Defaults to `lowercase_snake_case(name) + ".svg"`. Must match `^[a-zA-Z0-9_.-]+$`.
- `anchorBindings` — optional `k_*` anchor → node-UUID map.

```json
{
  "name": "create_diagram",
  "arguments": {
    "server": "<optional>",
    "projectId": "<project-uuid>",
    "name": "Tank 1 dashboard",
    "description": "Live level + pump state",
    "svg": "<svg xmlns=\"http://www.w3.org/2000/svg\" ...> ... </svg>",
    "anchorBindings": {
      "k_tank_level": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
      "k_pump_relay": "65691be2-f712-4727-8125-c94b66b3820e"
    }
  }
}
```
Response: `{"server": "<id>", "projectId": "<id>", "diagramId": "<new-uuid>", "name": "...", "fileName": "tank_1_dashboard.svg", "source": "https://.../project/<id>/diagram/tank_1_dashboard.svg", "anchorCount": N}`

**Precondition:** the `projectId` must exist. Call `list_projects` first, or create a project with `create_project` and reuse the returned id.

**Agent pattern:** write the generated SVG to `/tmp/<slug>.svg` first so you can iterate/diff/inspect it with ordinary file tools. Then read the tmp file back and pass its contents as `svg`. The tool handles upload + URL construction + node creation.

### `update_diagram`
Update an existing diagram. All meta fields are optional — omitted fields keep their current value. When `svg` is provided, the file is re-uploaded to the same path referenced by the existing `source` URL (so the URL stays stable for any embeds). Use `fileName` to rename the underlying file.

```json
{
  "name": "update_diagram",
  "arguments": {
    "diagramId": "<uuid>",
    "svg": "<svg>...updated markup...</svg>",
    "anchorBindings": {"k_tank_level": "...", "k_ammonia_ppm": "..."}
  }
}
```
Response: `{"server": "<id>", "diagramId": "<id>", "projectId": "<id>", "fileName": "...", "source": "https://...", "fileUploaded": bool, "updated": ["svg","anchorBindings", ...]}`

### `get_diagram`
Fetch a Diagram node's metadata AND the SVG content pointed to by its `source` URL — the full input you need to propose improvements.

```json
{"name": "get_diagram", "arguments": {"diagramId": "<uuid>"}}
```
Response: `{"server": "<id>", "diagramId": "<id>", "projectId": "<id>", "name": "...", "description": "...", "source": "https://.../project/<id>/diagram/<file>.svg", "fileName": "<file>.svg", "svg": "<svg>...</svg>", "anchorBindings": {...}}`

The `svg` field is the file content fetched from `source`; it's null if the URL points somewhere the MCP daemon can't reach (e.g. a stale URL from before a server rename, or an external CDN).

### `upload_diagram_file`
Stash a raw SVG at `/project/{id}/diagram/{file}` without creating a node. Useful for staging assets before deciding whether to wire them into a Diagram node. Prefer `create_diagram` for the normal case.
```json
{"name": "upload_diagram_file", "arguments": {"projectId": "<uuid>", "fileName": "floorplan.svg", "svg": "<svg>...</svg>"}}
```
Response includes the resulting public `url`.

### `download_diagram_file`
Download a raw SVG previously uploaded to `/project/{id}/diagram/{file}`.
```json
{"name": "download_diagram_file", "arguments": {"projectId": "<uuid>", "fileName": "floorplan.svg"}}
```
Response: `{"server": "<id>", "projectId": "<id>", "fileName": "...", "svg": "<svg>...</svg>", "bytes": N}`

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

## Troubleshooting

### `list_servers` returns `{"servers": []}`

**This is a known failure mode — a startup seed race.** `krill-mcp` seeds its server registry once at process start by probing each host in `/etc/krill-mcp/config.json`'s `seeds` list. If the Krill server it targets (typically `localhost:8442`) isn't yet listening when `krill-mcp` comes up, the probe fails with something like:

```
WARN KrillRegistry - Seed localhost:8442 /health failed:
  Failed to parse HTTP response: the server prematurely closed the connection
```

…and the registry stays empty until the process is restarted. The systemd unit declares `Wants=krill.service` but not `After=krill.service`, so a fresh boot routinely loses this race. There is no retry or re-probe loop in v0.0.5.

**Diagnose (on the krill-mcp host):**
```bash
journalctl -u krill-mcp --since "1 hour ago" | grep -iE "Seed|Registered"
```
If you see `Seed ... failed` without a matching `Registered Krill server`, the registry is empty — confirmed.

**Fix (requires shell access to the krill-mcp host):**
```bash
sudo systemctl restart krill-mcp
```
After Krill is healthy on `:8442`, restarting the MCP daemon re-seeds the registry. Do **not** restart shared services without the user's explicit go-ahead — ask first.

### No `add_server` / register tool exists

The registry is seed-config-driven and bootstrap-only. There is no MCP tool to add, remove, or re-probe a server at runtime. Don't waste turns looking for one. To add a new Krill server to an MCP install, the user has to edit `/etc/krill-mcp/config.json`'s `seeds` array and `sudo systemctl restart krill-mcp`.

### The `server` argument only resolves registered servers

`server: "id | host | host:port"` works only for servers already in the registry (seeded at startup). Passing a brand-new hostname does not register it — you'll get `"No Krill server matches '<selector>' (and no default is registered)."` Fix the seed list + restart.

### User named a Krill server by hostname, but it's only registered as `localhost`

The default seed is `localhost:8442`, so when `krill-mcp` is co-installed with a `krill` server, the registry key is `localhost` (plus whatever id `/health` returned) — **not** the box's actual hostname. A user who says "check pi-krill-05" thinks they're naming the Krill server, but from the MCP's view that host is just `localhost`.

Before reporting "server not found", try:
1. Call `list_servers` — if there's a `localhost`-hosted entry, use that id or pass `server: "localhost"`.
2. Pass the selector the user gave you; if it fails, retry with `"localhost"` (or no selector at all — `list_nodes` defaults to the first registered server).
3. When you use this fallback, tell the user plainly: "Your MCP registered this box as `localhost`, so I'm resolving `pi-krill-05` to it." They may want to reconfigure `/etc/krill-mcp/config.json`'s `seeds` to use the real hostname if the MCP ever needs to address the box from elsewhere.

## Direct-to-Krill fallback

When `krill-mcp` is unavailable or its registry is empty, you can hit the Krill server's REST API directly with the same bearer token. This skips every MCP tool but gets the user unblocked:

```bash
TOKEN=$(cat ~/.krill/pin_token)   # or read from /etc/krill/credentials/pin_derived_key
curl -sk -H "Authorization: Bearer $TOKEN" https://<host>:8442/nodes | jq
curl -sk -H "Authorization: Bearer $TOKEN" https://<host>:8442/health | jq
curl -sk -H "Authorization: Bearer $TOKEN" "https://<host>:8442/node/<uuid>/data/series?st=<ms>&et=<ms>" | jq
```

The `-k` is required because Krill uses a self-signed cert. Routes worth knowing when falling back:
- `GET /nodes` — same shape as MCP `list_nodes`
- `GET /node/{id}` — same as MCP `get_node`
- `GET /node/{id}/data/series?st=<ms>&et=<ms>` — same as MCP `read_series`
- `GET /health` — same as MCP `server_health`
- `POST /node/{id}` — upsert; body is the full Node JSON (what MCP `create_project` / `create_diagram` wrap)
- `PUT /project/{id}/diagram/{file}` (`Content-Type: image/svg+xml`) — what MCP `upload_diagram_file` wraps

When you use the fallback, tell the user so they know `krill-mcp` still needs attention.

## What's NOT here yet

No write tools for DataPoints, Triggers, Filters, Executors, Pins, TaskLists, Journals, or Cameras. If the user asks to "set up an alarm", emit the configuration as JSON and instruct them to apply it manually in the Krill app — then note that broader write support is a future capability.
