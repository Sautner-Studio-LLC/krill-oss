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

**If no MCP connector is registered in the current session**, don't give up and don't probe random Krill REST routes. Go straight to the **"Direct-to-MCP JSON-RPC fallback"** section at the bottom of this file — POST JSON-RPC 2.0 envelopes to `http://<host>:50052/mcp` with `Accept: application/json, text/event-stream` and the bearer, and **every MCP tool including writes** (`create_node`, `record_snapshot`, `delete_node`, `update_diagram`) is callable. The "Direct-to-Krill fallback" below it is the second-level workaround for when `krill-mcp` itself is down; it does not cover snapshot writes.

## Read tools (v0.0.8)

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

### `reseed_servers`
Force `krill-mcp` to re-probe every configured seed in `/etc/krill-mcp/config.json` and rebuild its registry. Use this when `list_servers` comes back empty after startup — on cold boot the MCP can race ahead of the `krill` server it targets, miss its probe, and sit with an empty registry until restarted. Calling `reseed_servers` recovers without shell access.
```json
{"name": "reseed_servers", "arguments": {}}
```
Response: `{"before": N, "after": M, "servers": [{"id": "...", "baseUrl": "...", "publicBaseUrl": "..."}, ...]}`

## Project + Diagram write tools (v0.0.8)

Specialized Project and Diagram write tools — prefer these over the generic `create_node` for those two types, because `create_diagram` does the SVG file upload + `meta.source` URL construction that `create_node` doesn't.

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

End-to-end: **always** uploads the SVG to `/project/{projectId}/diagram/{fileName}` (filename defaults to `slug(name) + ".svg"` unless `uploadFileName` is given), then posts a `KrillApp.Project.Diagram` node whose `meta.source` is the resulting public URL. **`DiagramMetaData.source` is a URL**, not inline markup — this tool handles the file write + URL construction so you pass plain SVG bytes as `source`.

**Anchor contract (embedded in the tool description too):**
- Anchors are **bare** `<rect id="k_<node-uuid>" fill="none"/>` — no children, no text, no stroke. The Krill client overlays the live UI inside the rect at runtime.
- `anchorBindings` maps `anchor_id → node_uuid`. With the `k_<uuid>` id convention, both sides are the same uuid.

Parameters:
- `source` — the SVG content (required). Must contain a `<svg>` tag. 2 MB cap server-side.
- `uploadFileName` — optional filename override, e.g. `aquarium.svg`. Defaults to `slug(name)+.svg`. Must match `^[a-zA-Z0-9_.-]+$`. **Upload happens regardless** — this only controls where the file lands.
- `anchorBindings` — optional `k_<uuid>` anchor id → bound node-UUID map.

```json
{
  "name": "create_diagram",
  "arguments": {
    "server": "<optional>",
    "projectId": "<project-uuid>",
    "name": "Tank 1 dashboard",
    "description": "Live level + pump state",
    "source": "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect id=\"k_1c9dce76-ba65-48b5-b842-32ad97a96f80\" x=\"30\" y=\"30\" width=\"280\" height=\"140\" fill=\"none\"/></svg>",
    "anchorBindings": {
      "k_1c9dce76-ba65-48b5-b842-32ad97a96f80": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
      "k_65691be2-f712-4727-8125-c94b66b3820e": "65691be2-f712-4727-8125-c94b66b3820e"
    }
  }
}
```
Response: `{"server": "<id>", "projectId": "<id>", "diagramId": "<new-uuid>", "name": "...", "fileName": "tank_1_dashboard.svg", "source": "https://.../project/<id>/diagram/tank_1_dashboard.svg", "anchorCount": N, "anchorBindings": {...}, "sourceBytes": N}`

**Precondition:** the `projectId` must exist. Call `list_projects` first, or create a project with `create_project` and reuse the returned id.

**Agent pattern:** write the generated SVG to `/tmp/<slug>.svg` first so you can iterate/diff/inspect it with ordinary file tools. Then read the tmp file back and pass its contents as `source`. The tool handles upload + URL construction + node creation.

### `update_diagram`

Update an existing diagram. All meta fields are optional; omitted fields keep their current value. When `source` is provided, the file is **always** re-uploaded — by default to the same path the current `meta.source` URL references, so the URL stays stable and any existing embeds keep working. Pass `uploadFileName` only to rename the file.

The tool starts from the existing node's `meta` and mutates the requested fields in place, which preserves the polymorphic discriminator the server emits. (Rebuilding `meta` from scratch caused a silent `anchorBindings` no-op in v0.0.5 — v0.0.6 fixes that.)

```json
{
  "name": "update_diagram",
  "arguments": {
    "diagramId": "<uuid>",
    "source": "<svg>...updated markup...</svg>",
    "anchorBindings": {
      "k_1c9dce76-ba65-48b5-b842-32ad97a96f80": "1c9dce76-ba65-48b5-b842-32ad97a96f80"
    }
  }
}
```
Response: `{"server": "<id>", "diagramId": "<id>", "projectId": "<id>", "fileName": "...", "source": "https://...", "fileUploaded": bool, "sourceBytes": N?, "anchorCount": N, "anchorBindings": {...}, "updated": ["source","anchorBindings", ...]}`

The response echoes the full post-update `anchorBindings` map and `anchorCount` so you can verify the write took without a separate `get_diagram` round-trip.

### `get_diagram`
Fetch a Diagram node's metadata AND the SVG content pointed to by its `source` URL — the full input you need to propose improvements.

```json
{"name": "get_diagram", "arguments": {"diagramId": "<uuid>"}}
```
Response: `{"server": "<id>", "diagramId": "<id>", "projectId": "<id>", "name": "...", "description": "...", "source": "https://.../project/<id>/diagram/<file>.svg", "fileName": "<file>.svg", "svg": "<svg>...</svg>", "anchorBindings": {...}}`

The `svg` field is the file content fetched from `source` (the response field is still named `svg`, even though the input parameter on create/update is `source`); it's null if the URL points somewhere the MCP daemon can't reach (e.g. a stale URL from before a server rename, or an external CDN).

### `upload_diagram_file`
Stash a raw SVG at `/project/{id}/diagram/{file}` without creating a node. Useful for staging assets before deciding whether to wire them into a Diagram node. Prefer `create_diagram` for the normal case.
```json
{"name": "upload_diagram_file", "arguments": {"projectId": "<uuid>", "fileName": "floorplan.svg", "source": "<svg>...</svg>"}}
```
Response includes the resulting public `url`. Note: on this tool the filename parameter is `fileName` (not `uploadFileName`), but the SVG body parameter is `source` — same as create/update.

### `download_diagram_file`
Download a raw SVG previously uploaded to `/project/{id}/diagram/{file}`.
```json
{"name": "download_diagram_file", "arguments": {"projectId": "<uuid>", "fileName": "floorplan.svg"}}
```
Response: `{"server": "<id>", "projectId": "<id>", "fileName": "...", "svg": "<svg>...</svg>", "bytes": N}`

## Generic node write tools (v0.0.8)

The `create_node` / `list_node_types` pair covers every `KrillApp.*` type that's registered in the server's polymorphic serializer. For Projects and Diagrams, keep using the specialized tools above — `create_diagram` in particular handles SVG file upload + `meta.source` URL construction that `create_node` doesn't.

### `list_node_types`
Return the full creatable-type catalog the MCP knows about: short name, MetaData class FQN, role, side-effect level, description, valid parent/child types, and the default meta skeleton. Call this first when you don't know which `KrillApp.*` type to use — it's authoritative for *this* MCP version, whereas `references/node-types/` is the static companion catalog that may lag a server-side change.
```json
{"name": "list_node_types", "arguments": {"role": "trigger", "contains": "threshold"}}
```
Both filters are optional and case-insensitive. Response: `{"count": N, "types": [{"shortName": "KrillApp.Trigger.HighThreshold", "typeFqn": "...", "metaFqn": "...", "role": "trigger", "sideEffect": "low", "description": "...", "validParentTypes": [...], "validChildTypes": [...], "defaultMeta": {...}, "notes": "..."}, ...]}`

### `create_node`
Create a node of any registered type. Provide `type` (short name or FQN), `parent` (id of an existing node on the same server), optional `name`, and optional `meta` overlay.

- `meta` is **shallow-merged** over the type's default meta skeleton. The `type` key (polymorphic discriminator) inside `meta` is always overwritten by the tool — callers can't break it.
- A few MetaData classes have no `name` field (MQTT, Compute, Lambda, SMTP, LLM). Passing `name` on those types is silently dropped by the server's `ignoreUnknownKeys = true`.
- If the parent's type isn't in the `validParentTypes` list for the requested type, the tool returns a `warnings[]` entry but still posts — the server will accept the node and the catalog may simply be behind.

```json
{
  "name": "create_node",
  "arguments": {
    "server": "<optional>",
    "type": "KrillApp.DataPoint",
    "parent": "<server-uuid-or-other-parent>",
    "name": "Aquarium temp",
    "meta": {"dataType": "DOUBLE", "unit": "°C", "precision": 1, "manualEntry": false}
  }
}
```
Response: `{"server": "<id>", "nodeId": "<new-uuid>", "type": "KrillApp.DataPoint", "parent": "<id>", "parentType": {"fqn": "...", "shortName": "..."}, "meta": {...final merged...}, "warnings"?: [...]}`

**Authoring a multi-node tree (parent-first):**
1. `create_node type=KrillApp.DataPoint parent=<serverId> name="Aquarium temp" meta={dataType:"DOUBLE",unit:"°C"}` → record the returned `nodeId` as `dpId`.
2. `create_node type=KrillApp.Trigger.HighThreshold parent=<dpId> name="Overheat" meta={value:30.0}` → record `tId`.
3. `create_node type=KrillApp.Executor.OutgoingWebHook parent=<tId> name="Page me" meta={url:"https://...",method:"POST"}`.
4. `get_node` each new id to confirm persistence — the create response echoes what was sent, not what the server stored.

### `execute_node`

Manually fire a Trigger or Executor once — equivalent to tapping the manual-execute button in the Krill app. Use when the user wants to run a `KrillApp.Executor.LogicGate`, `KrillApp.Executor.Lambda`, `KrillApp.Trigger.Button`, etc., **right now** without waiting for the trigger chain to drive it.

Wire shape: the tool fetches the existing node body, sets `state="EXECUTED"`, refreshes `timestamp`, and POSTs it back to `/node/{id}`. The server's `update()` runs `node.type.emit(node)` on every upsert, so the per-type processor (`ServerExecutorProcessor`, `LogicGateProcessor`, etc.) reacts to `EXECUTED` and runs the action — no new server endpoint needed. The Compose client uses the same wire pattern when its UI fires a node.

```json
{"name": "execute_node", "arguments": {"server": "<optional>", "id": "<node-uuid>"}}
```

Response: `{"server": "<id>", "nodeId": "<id>", "type": "krill.zone.shared.KrillApp.Executor.LogicGate", "priorState": "<NONE|EXECUTED|...>", "requestedState": "EXECUTED", "firedAt": <epoch-ms>, "node": <full post-fire node>, "note": "..."}`

**When to reach for this vs. `record_snapshot`:**
- `execute_node` is the right primitive for *"fire this gate / run this lambda once"*. It runs the action directly without polluting any DataPoint's history.
- `record_snapshot` is the right primitive for *"observe a new sensor reading"*. The server-side trigger evaluation chain runs as a side effect of the value landing.

If the chain you care about happens to read upstream from a DataPoint, you *could* use `record_snapshot` to drive it indirectly — but that adds a synthetic sample to the time-series store and won't work at all for executors whose source is a derived/computed value with no DataPoint to write to. Default to `execute_node` for manual fire and reserve `record_snapshot` for actual readings.

**Type filter — what's NOT firable:**
- `KrillApp.Server`, `KrillApp.Client`, `KrillApp.Client.About`, `KrillApp.Server.Peer`, `KrillApp.Server.Backup`. These are containers/infrastructure; firing them is a no-op or semantically wrong. The tool returns `ERROR: Node type ... does not support manual execution.` and refuses to POST.
- Everything else is allowed. Block-list, not allow-list — new node types ship without a coordinated MCP release, so unknown FQNs default to firable.

**Verifying the fire took:** the POST returns `202` before the per-type processor runs. The `node` field in the response is the post-fire GET, but actions with downstream effects (a LogicGate flipping a Pin's state, an Executor recording a snapshot on its target) take ~1s to settle. For those, follow up with `get_node` on the *target* (`meta.targets[0].nodeId` for a LogicGate / Trigger) — the target's `state` and `meta.snapshot` are where the visible change lands.

### `record_snapshot`
Record one or many values on an existing `KrillApp.DataPoint`. Each snapshot becomes a new point in the time-series store and runs through the DataPoint's child Filters + Triggers.

- Single value: `{"dataPointId": "<uuid>", "value": 42.5, "timestamp": 1776700000000}` (timestamp optional; defaults to now in ms).
- Series: `{"dataPointId": "<uuid>", "snapshots": [{"timestamp": 1776700000000, "value": 22.1}, {"timestamp": 1776700060000, "value": 22.3}]}`. Required `timestamp` is epoch **milliseconds**.
- `value` is coerced to string on the wire. **Client-side validation mirrors the server** — if any snapshot fails, **nothing is posted**:
  - TEXT → non-empty string
  - JSON → non-empty string
  - DIGITAL → 0 or 1 (booleans auto-map to 0/1)
  - DOUBLE → must parse as Double
  - COLOR → decimal string of a 24-bit RGB integer, `(R<<16) | (G<<8) | B`. See the "COLOR snapshot encoding" note below.

### COLOR snapshot encoding

For `DataType.COLOR` DataPoints, `Snapshot.value` is the **decimal string of a packed 24-bit RGB integer** — one channel per byte, no alpha:

```
value = (R << 16) | (G << 8) | B          // each channel 0–255
```

Stringify that integer with normal decimal (`.toString()`), not hex, not `#RRGGBB`. Examples:

| Color    | Hex       | Snapshot `value` |
|----------|-----------|------------------|
| black    | `0x000000`| `"0"`            |
| white    | `0xFFFFFF`| `"16777215"`     |
| red      | `0xFF0000`| `"16711680"`     |
| green    | `0x00FF00`| `"65280"`        |
| blue     | `0x0000FF`| `"255"`          |
| yellow   | `0xB3B800`| `"11778048"`     |

Server-side validation is just `value.toLong()`, so any parseable Long is accepted — but values outside 24 bits are silently masked off (`value.toLong() and 0xFFFFFFL`) when the UI renders the swatch, so don't pack alpha or anything above bit 23. The Krill client reconstitutes opaque alpha (`0xFF000000`) at render time.

**Don't mistake the widened high byte for alpha.** A stored value like `11778048` looks like `0x00B3B800` when you format the Long as 8 hex digits. The leading `00` is **not an alpha byte** — stored values never carry alpha, and the high byte of a sub-16M integer is simply zero. If you try to read it as ARGB and "fix" the alpha to opaque, you'll write something like `0xFFB3B800` (decimal `4290114560`) — harmless, because the renderer masks away everything above bit 23, but it's wasted reasoning and brittle. Model the value as three concatenated bytes R·G·B; ignore the Long's upper bits.

**When in doubt, mirror the existing value.** If the user already has a COLOR DataPoint on the swarm, call `get_node` on it and look at `meta.snapshot.value` — whatever you see there is exactly the format to write back. Copy that encoding, don't re-derive it.

```json
{
  "name": "record_snapshot",
  "arguments": {
    "dataPointId": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
    "snapshots": [
      {"timestamp": 1776700000000, "value": 22.1},
      {"timestamp": 1776700060000, "value": 22.3},
      {"timestamp": 1776700120000, "value": 22.4}
    ]
  }
}
```
Response: `{"server": "<id>", "dataPointId": "<id>", "dataType": "DOUBLE", "submitted": 3, "snapshots": [{"timestamp": ..., "value": "..."}], "note": "..."}`

**Async ingest caveat.** Every POST returns `202 Accepted` before the server's ingest pipeline runs. Two observed behaviors:
- **First `read_series` immediately after a POST often returns 0 snapshots, even on a bare DataPoint.** The server's `scope.launch`-based ingest isn't committed yet. **Wait ~1.5 seconds then re-read**, or retry once. The second read reliably sees the committed values. A zero-then-populated pattern is normal, not a failed write.
- **Persistent drops happen on filter children.** If the DataPoint has a `DiscardAbove` / `DiscardBelow` / `Deadband` / `Debounce` child, those filters can silently reject a snapshot — `read_series` will still return 0 after retry. That's a real drop, not an ingest-delay false negative. Inspect the filter children before concluding why a value didn't land.

**No REST equivalent — JSON-RPC only.** Unlike reads and node upserts, snapshot writes have no `POST /node/{id}/data`-style route on `:8442`. If MCP tools aren't in-session, use the **Direct-to-MCP JSON-RPC fallback** (see below) — don't waste time probing `/snapshot`, `/data/record`, etc. on the Krill server; they all 404.

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

Startup seed race: `krill-mcp` seeds its registry at process start by probing each host in `/etc/krill-mcp/config.json`'s `seeds` list. If the Krill server it targets (typically `localhost:8442`) isn't yet listening when `krill-mcp` comes up, the probe can miss.

**v0.0.6 mitigations** (mostly self-healing):
- The systemd unit declares `After=krill.service` so `krill-mcp` waits for Krill to start.
- Bootstrap runs up to 5 probe attempts with 2s backoff per seed.
- `resolve(selector)` lazy-reprobes seeds when the registry is empty.
- An MCP tool (`reseed_servers`) forces a full re-probe without shell access.

**If `list_servers` is still empty, in order of preference:**
1. Call `reseed_servers`. No shell needed. Watch the response `after` count.
2. If reseed still returns 0 servers, the Krill server on `:8442` is likely down. Ask the user to check `systemctl status krill` on the box, or fall back to the direct-to-Krill workflow below.
3. Last resort with shell access: `sudo systemctl restart krill-mcp`. Do **not** restart shared services without the user's explicit go-ahead.

**Diagnose (on the krill-mcp host, shell only):**
```bash
journalctl -u krill-mcp --since "1 hour ago" | grep -iE "Seed|Registered"
```
If you see `Seed ... failed` N times without a matching `Registered Krill server`, the probe genuinely can't reach Krill — look at the Krill server side.

### Adding a new Krill server at runtime

There is no `add_server` MCP tool. Adding a new server means editing `/etc/krill-mcp/config.json`'s `seeds` array and running either `reseed_servers` (if the new seed is reachable now) or `sudo systemctl restart krill-mcp`. `reseed_servers` is the no-shell path.

### The `server` argument only resolves registered servers

`server: "id | host | host:port"` works only for servers already in the registry (seeded at startup). Passing a brand-new hostname does not register it — you'll get `"No Krill server matches '<selector>' (and no default is registered)."` Fix the seed list + restart.

### Recovery: write didn't land

If `update_diagram` / `create_diagram` returns cleanly but a follow-up `get_diagram` shows the old state, the write silently didn't persist — a v0.0.5-class bug. v0.0.6 fixed the known occurrence, but the recovery path stays relevant because node metadata and file bytes are stored separately and can drift. **Always round-trip with `get_diagram` after a write** and diff against what you sent.

If the round-trip shows a mismatch:

**1. File bytes not refreshed (served SVG still the old one):**
```bash
TOKEN=$(cat ~/.krill/pin_token)
curl -sk -X PUT \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: image/svg+xml" \
  --data-binary @/tmp/<slug>.svg \
  "https://<host>:8442/project/<projectId>/diagram/<filename>.svg"
# → HTTP 201 "File uploaded successfully"
```
This writes to exactly the path `meta.source` already references, so no metadata has to change — Krill clients pick up the new bytes on their next load. Safe escape hatch whether `update_diagram` is healthy or not. Also the preferred route for SVGs over ~50 KB (avoids passing the whole payload through a tool call).

**2. Node metadata (e.g. `anchorBindings`) not persisted:**
```bash
# Fetch current node
curl -sk -H "Authorization: Bearer $TOKEN" "https://<host>:8442/node/<diagramId>" > /tmp/diag.json

# Edit /tmp/diag.json — replace meta.anchorBindings with the map you want, bump timestamp
python3 -c "
import json, time
d = json.load(open('/tmp/diag.json'))
d['meta']['anchorBindings'] = {'k_<uuid>': '<uuid>'}  # your target map
d['timestamp'] = int(time.time() * 1000)
json.dump(d, open('/tmp/diag.json', 'w'))
"

# POST the full body back — Krill upserts; the server replaces what it has
curl -sk -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  --data-binary @/tmp/diag.json \
  "https://<host>:8442/node/<diagramId>"
# → HTTP 202
```
Post the whole node body (not a partial/PATCH) — Krill's REST upserts on POST and replaces the record. This sidesteps any server-side merge logic.

After either recovery, round-trip `get_diagram` once more to confirm the state is what you intended, then tell the user which path you took.

### User named a Krill server by hostname, but it's only registered as `localhost`

The default seed is `localhost:8442`, so when `krill-mcp` is co-installed with a `krill` server, the registry key is `localhost` (plus whatever id `/health` returned) — **not** the box's actual hostname. A user who says "check pi-krill-05" thinks they're naming the Krill server, but from the MCP's view that host is just `localhost`.

Before reporting "server not found", try:
1. Call `list_servers` — if there's a `localhost`-hosted entry, use that id or pass `server: "localhost"`.
2. Pass the selector the user gave you; if it fails, retry with `"localhost"` (or no selector at all — `list_nodes` defaults to the first registered server).
3. When you use this fallback, tell the user plainly: "Your MCP registered this box as `localhost`, so I'm resolving `pi-krill-05` to it." They may want to reconfigure `/etc/krill-mcp/config.json`'s `seeds` to use the real hostname if the MCP ever needs to address the box from elsewhere.

## Direct-to-MCP JSON-RPC fallback (preferred when write tools are needed)

When no Claude-side MCP connector is registered in the current session but `krill-mcp` is running and reachable on the LAN, you can invoke every MCP tool — including `create_node`, `record_snapshot`, `update_diagram` — by POSTing JSON-RPC 2.0 envelopes straight to `http://<host>:50052/mcp` with the same bearer. This is **the preferred fallback for any write operation**, because the Krill REST API on `:8442` does not expose a snapshot-write endpoint (see the `record_snapshot` row in the "No REST equivalent" note below).

Envelope and headers:

```bash
TOKEN=$(cat ~/.krill/pin_token)
MCP_HOST=pi-krill-05.local:50052       # or wherever krill-mcp listens
JSONRPC_ID=1

curl -s -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  "http://$MCP_HOST/mcp" \
  --data @- <<JSON
{
  "jsonrpc": "2.0",
  "id": $JSONRPC_ID,
  "method": "tools/call",
  "params": {
    "name": "record_snapshot",
    "arguments": {
      "dataPointId": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
      "snapshots": [
        {"timestamp": 1776700000000, "value": 22.1},
        {"timestamp": 1776700060000, "value": 22.3}
      ]
    }
  }
}
JSON
```

Notes:

- **`:50052/mcp` is plain HTTP.** `krill-mcp` doesn't wrap its own TLS — security comes from the bearer. No `-k`. (The underlying Krill server on `:8442` is HTTPS self-signed; different box of the same shared secret.)
- **Dual Accept header.** `application/json, text/event-stream` is the MCP Streamable HTTP spec's recommended value. `krill-mcp` itself is lenient and accepts plain `application/json`, but some MCP transports / proxies in front of it aren't — always send both.
- **Method surface.** `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`. Use `tools/list` first when exploring so you know the exact argument shapes:
  ```bash
  curl -s ... --data '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' http://$MCP_HOST/mcp | jq
  ```
- **Response shape.** A JSON-RPC result with `.result.content[0].text` containing the tool's JSON response (stringified), or `.result.isError = true` with an `ERROR: <message>` text block.
- **Notifications get 202 with no body.** That's correct per JSON-RPC — a payload with no `id` (e.g. `notifications/initialized`) returns nothing.
- **Report the fallback to the user.** "Your session doesn't have the krill-mcp connector registered, so I'm calling it over raw JSON-RPC at http://host:50052/mcp" — gives them the signal to add the Custom Connector for next time.

### No REST equivalent on `:8442` for these operations

The Krill server's REST API on `:8442` is **read + node-upsert + diagram-file** only. Operations below have no direct REST endpoint; the JSON-RPC fallback above is the only non-MCP-connector path:

| Operation | Krill REST? | JSON-RPC fallback tool |
|-----------|-------------|------------------------|
| Record a DataPoint snapshot value | **No** — there is no `/data`, `/snapshot`, `/data/record`, `/data/snapshots` route | `record_snapshot` |
| Manually fire a Trigger or Executor | Indirect — `POST /node/{id}` with the body's `state` set to `"EXECUTED"` (no dedicated `/execute` route). | `execute_node` |
| Create a node of arbitrary type | Yes (`POST /node/{id}` with full body) | `create_node` (simpler — handles discriminators + defaults) |
| Create a project / diagram / update diagram | Yes (`POST /node/{id}` + `PUT /project/{id}/diagram/{file}`) | `create_project` / `create_diagram` / `update_diagram` |
| Re-seed the MCP registry | N/A (MCP-only concern) | `reseed_servers` |

If you catch yourself probing `POST /node/<uuid>/data` or `/snapshots` or similar against `:8442` — stop. They return 404. Write via the MCP tools (natively or through JSON-RPC), not against the Krill server directly.

## Direct-to-Krill fallback

When **`krill-mcp` itself is down** (service stopped, box unreachable) but the underlying Krill server on `:8442` is up, hit the Krill REST API directly with the same bearer token. This covers reads and non-snapshot node upserts — for snapshot writes you need the JSON-RPC fallback above instead. If `krill-mcp` is up but its registry is empty, prefer the JSON-RPC fallback above — it gives you every MCP tool including `reseed_servers`, which a direct REST call can't recover.

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
- `POST /node/{id}` — upsert; body is the full Node JSON (what MCP `create_project` / `create_diagram` / `create_node` wrap)
- `PUT /project/{id}/diagram/{file}` (`Content-Type: image/svg+xml`) — what MCP `upload_diagram_file` wraps
- `DELETE /node/{id}` — body is the full Node JSON

When you use the fallback, tell the user so they know `krill-mcp` still needs attention.

### REST fallback for `create_node`

Posting a brand-new node by hand is a POST to `/node/{id}` with a self-generated UUID, `state: "CREATED"`, the right polymorphic discriminators, and a meta object shaped for the target MetaData class. Example — a DataPoint directly under the server:

```bash
TOKEN=$(cat ~/.krill/pin_token)
HOST=pi-krill-05.local:8442                   # whichever Krill server
SERVER_ID=$(curl -sk -H "Authorization: Bearer $TOKEN" https://$HOST/health | jq -r .id)
NEW_ID=$(uuidgen)

curl -sk -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  https://$HOST/node/$NEW_ID \
  -d @- <<JSON
{
  "id": "$NEW_ID",
  "parent": "$SERVER_ID",
  "host": "$SERVER_ID",
  "type": { "type": "krill.zone.shared.KrillApp.DataPoint" },
  "state": "CREATED",
  "meta": {
    "type": "krill.zone.shared.krillapp.datapoint.DataPointMetaData",
    "name": "test",
    "snapshot": { "timestamp": 0, "value": "0.0" },
    "dataType": "DOUBLE",
    "precision": 2,
    "unit": "",
    "manualEntry": true,
    "maxAge": 0,
    "path": "",
    "sourceId": "",
    "error": ""
  },
  "timestamp": 0
}
JSON

# ALWAYS round-trip to verify — the POST returns 202 with an empty body,
# and the server backfills defaults for any meta fields you omitted
# (e.g. if you skip `snapshot`, it comes back as {timestamp: 0, value: ""}).
curl -sk -H "Authorization: Bearer $TOKEN" https://$HOST/node/$NEW_ID | jq
```

To derive the JSON shape for a different type, fetch an existing node of that type with `GET /node/{id}` and copy its structure. The two polymorphic discriminators on the wire are:
- `node.type.type` — fully-qualified class name of the `KrillApp.*` data object (`krill.zone.shared.KrillApp.Trigger.HighThreshold`, etc.). Qualified name uses mixed case — `KrillApp` is the outer class.
- `node.meta.type` — fully-qualified class name of the MetaData class (all lowercase `krill.zone.shared.krillapp.<subpath>.<Type>MetaData`). Not all KrillApp types have a uniquely-named MetaData: every Trigger uses `TriggerMetaData`, every Filter uses `FilterMetaData`. `list_node_types` (when the MCP is reachable) is authoritative; when it isn't, `references/node-types/INDEX.md` + a sample `GET /node/{id}` of the same type is the best source.

**The `create_node` round-trip rule applies to the REST path too.** Always `GET /node/{id}` after a POST to see what the server actually stored — the echo of your POST is not authoritative. Same goes for `record_snapshot` / DataPoint value posts: use `GET /node/{id}/data/series?st=...&et=...` to confirm a snapshot actually landed (child Filters can drop it silently).

## What's NOT here yet

- **Delete.** No `delete_node` tool. Point the user at the Krill app's delete UI. The server supports `DELETE /node/{id}` with the full Node in the body, but this MCP doesn't wrap it.
- **Update in place.** `create_node` is for brand-new nodes (posts `state=CREATED`). Updating an existing non-Diagram node means fetching it, mutating meta, and POSTing back — no high-level helper for that yet. `update_diagram` is the one exception.
- **Server-side join / bulk create.** Each `create_node` is a single HTTP POST. Large trees are N posts; no transactional "create this subtree" primitive.
