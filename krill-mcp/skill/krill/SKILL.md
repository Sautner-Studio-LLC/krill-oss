---
name: krill
version: 0.0.8
description: Use when working with a Krill swarm — the home-automation/IoT system whose nodes are reachable via the krill-mcp Model Context Protocol server (typically http://<host>:50052/mcp, see https://krillswarm.com). Invoke for discovering or inspecting Krill servers, nodes, DataPoints, Triggers, Filters, Executors, Pins, or peers; reading time-series sensor data; reasoning about which node type to use for a given automation; and authoring, uploading, downloading, and improving SVG dashboards (Diagram nodes) that overlay live node state on a custom layout. Triggers on keywords like krill, swarm, krill server, krill node, krill-mcp, DataPoint, Trigger threshold, SVG dashboard, k_ anchor, swarm sensor, pi-krill, create project, create diagram, improve diagram.
---

<!--
The `version` field above tracks the krill-mcp server release this skill was
authored against. When bumping krill-mcp, also bump this file's frontmatter
`version` and every `vX.Y.Z` mention in this file and references/mcp-tools.md.
See krill-mcp/README.md → "Version sync" for the full checklist.
-->

# Krill skill

Krill is a peer-to-peer home-automation / IoT platform built around a tree of typed nodes. Each Krill server runs as a peer in a swarm; nodes describe sensors, computations, triggers, executors, and visualizations. This skill lets Claude discover and reason about a live Krill swarm via the **krill-mcp** Model Context Protocol server, and author SVG dashboards bound to live node state.

## When to invoke

- The user mentions a Krill swarm, a specific Krill server (e.g. `pi-krill-05.local`), or any Krill node type by name.
- The user wants to see what sensors / nodes exist, read a value, or understand a trigger or executor's current state.
- The user wants a visual dashboard built on top of their swarm — almost always means a `KrillApp.Project.Diagram` SVG.
- The user asks "what kind of node should I use to ..." — answer from the bundled node-type catalog instead of guessing.

Do **not** invoke for generic IoT/home-automation questions that don't reference Krill — answer those directly.

### Check for MCP tools *before* telling the user to install anything

Before assuming the session needs a Custom Connector, verify what's actually available:

1. Probe for MCP tools in the current session (e.g. search for `list_servers`, `create_node`, `list_node_types`, `read_series`). If they're present, use them.
2. If they're absent but `krill-mcp` is running on the LAN, use the **Direct-to-MCP JSON-RPC fallback** — POST JSON-RPC envelopes to `http://<host>:50052/mcp` with the same bearer. This gives you **every MCP tool including writes** (`create_node`, `record_snapshot`, `update_diagram`). This is the preferred fallback for anything that mutates state. See `references/mcp-tools.md` → "Direct-to-MCP JSON-RPC fallback" for the exact envelope and headers.
3. If `krill-mcp` itself is down but a Krill server on `:8442` is reachable, use the **Direct-to-Krill REST fallback** — but note the hard limits: **no endpoint exists for snapshot writes** (all attempts at `/data`, `/snapshot`, `/data/record`, etc. 404). REST fallback covers reads and generic `POST /node/{id}` upserts. See `references/mcp-tools.md` → "Direct-to-Krill fallback".
4. Only ask the user to install / register the MCP connector when **none** of the above work (e.g. neither `:50052/mcp` nor `:8442` is reachable).

Bearer-token locations checked in order: `~/.krill/pin_token`, then `sudo krill-mcp-token` on the krill-mcp host, then `/etc/krill/credentials/pin_derived_key` on a Krill server.

## Bundled references

Read these on demand — they're not auto-loaded:

- **`references/mcp-tools.md`** — the MCP tools (read: `list_servers`, `list_nodes`, `get_node`, `read_series`, `server_health`, `reseed_servers`; write: `list_projects`, `create_project`, `create_diagram`, `update_diagram`, `get_diagram`, `upload_diagram_file`, `download_diagram_file`), their JSON shapes, the standard discovery + diagram flows, auth setup. Read this first whenever you need to query or mutate a swarm.
- **`references/node-types/INDEX.md`** — table of all 37 Krill node types grouped by role (state / trigger / action / filter / display / container / infra) with one-liners and side-effect levels. Read this when the user's request needs a node type and you don't already know which one applies.
- **`references/node-types/KrillApp.<Type>.json`** — full spec for one node type: purpose, behavior, inputs, outputs, valid parents/children, side-effect level, examples. Read after narrowing via the index, before recommending the type to the user.
- **`references/dashboard-conventions.md`** — the `k_*` anchor convention, `DiagramMetaData` shape, authoring guidance, reference example. Read before generating any SVG dashboard.

## Standard workflow

### For a discovery / inspection request
1. If MCP tools are available in this Claude session, call `list_servers` → `server_health` → `list_nodes` (with a `type` filter when the user's intent narrows it). Use `get_node` for specific nodes the user names. Use `read_series` only when historical values matter.
2. **If `list_servers` returns `{"servers": []}`** — call `reseed_servers` first. v0.0.6 mitigates the startup seed race with `After=krill.service`, bootstrap retries, and lazy re-probe on miss, but if the registry is still empty, `reseed_servers` forces a full re-probe without shell access. Only ask the user to `systemctl restart krill-mcp` after `reseed_servers` returns 0 servers AND the Krill server appears to be up (check via the direct-to-krill fallback below).
3. **If the user names a Krill server (e.g. "check pi-krill-05") and it's not in `list_servers`** — before giving up, try `list_nodes` with `server: "localhost"` (or look in `list_servers` for a `localhost`-hosted entry). The default seed is `localhost:8442`, so a krill-mcp co-installed with a `krill` server registers the host under that alias — the user may have named the box thinking of it as the Krill server while the MCP only knows it as `localhost`. If that succeeds, tell the user you resolved their hostname to the MCP's local entry so they're not surprised.
4. **If MCP tools are not configured** in the session, use the **Direct-to-MCP JSON-RPC fallback** (`references/mcp-tools.md` → "Direct-to-MCP JSON-RPC fallback") — POST JSON-RPC envelopes to `http://<host>:50052/mcp` with `Accept: application/json, text/event-stream` and the bearer. This gives you **every MCP tool including writes**, not just reads. Then tell the user how to add the Custom Connector so it works natively next time.
5. **If `krill-mcp` itself is wedged** (service stopped, or unreachable) but the underlying Krill server on `:8442` is up, fall back to hitting the Krill REST API directly — `curl -sk -H "Authorization: Bearer $TOKEN" https://<host>:8442/nodes`. Reads + generic node upserts work; **snapshot writes do NOT** (no `/data`, `/snapshot`, `/data/record` route exists — they 404). Details in `references/mcp-tools.md` → "Direct-to-Krill fallback".
6. Translate type strings like `krill.zone.shared.KrillApp.DataPoint` into the human catalog entry from `references/node-types/INDEX.md` when explaining results.

### For SVG dashboard requests (create / improve)

**Key invariant:** `DiagramMetaData.source` is a **URL**, not inline SVG. Krill stores the SVG as a file on the server and renders it by fetching that URL. The `create_diagram` and `update_diagram` tools handle the full two-step (upload file → post node with URL) — don't try to cram SVG markup into `source` yourself.

1. Call `list_nodes` to enumerate every node the user might want on the dashboard (DataPoints, Graphs, Triggers, Executors — whatever lives under the target project). Capture each one's id, name, and type.
2. Read `references/dashboard-conventions.md` — the anchor contract (`<rect id="k_<node-uuid>" fill="none"/>`, no inner content, Krill overlays live UI), `DiagramMetaData.anchorBindings: Map<String,String>`, the URL-not-inline contract, runtime behavior.
3. **Pick the parent project.** Diagrams must be children of a `KrillApp.Project` container.
   - Call `list_projects` first. If exactly one project exists, use it (announce the choice). If several, ask the user which one. If none exist, offer to create one and call `create_project` with a sensible name.
4. Ask the user (or infer) the layout: floor plan, equipment diagram, grid of tiles, themed illustration. Remember: you only decide where each node sits and how big — the client renders everything inside the anchor. Default to a clean dark-background tile grid if no preference.
5. **Stage the SVG in `/tmp/<slug>.svg`** where `<slug>` is the lowercase_snake_case of the diagram name. Writing a real file first makes it easy to re-read, diff across iterations, and hand-inspect before shipping — nothing is committed until you call `create_diagram`. Use the same tmp path across edits so intermediate work isn't lost.
6. Emit one bare `<rect id="k_<node-uuid>" x=".." y=".." width=".." height=".." fill="none"/>` per bound node — no inner text, no placeholder graph, no tile chrome *on* the rect. Build `anchorBindings` as `{"k_<uuid>": "<uuid>", ...}`.
7. Call `create_diagram` with `{projectId, name, source: <contents of /tmp/<slug>.svg>, anchorBindings}`. The tool **always** uploads the file and constructs the URL; filename defaults to `slug(name)+".svg"`. Pass `uploadFileName` only to override the default filename.
8. For **improving an existing diagram**: call `get_diagram`, save the current `svg` to `/tmp/<slug>_before.svg`, edit into `/tmp/<slug>.svg`, reason about what to change (layout tweak, new anchors for newly-created DataPoints, visual cleanup), then call `update_diagram` with `{diagramId, source: <new markup>, anchorBindings?}`. Passing `source` re-uploads to the same filename the current URL references. Preserve anchor ids that are still in use — changing them without rewriting `anchorBindings` breaks the live overlay.
9. **Round-trip verify every write.** After `create_diagram` or `update_diagram`, call `get_diagram` and diff the returned `svg` + `anchorBindings` against what you sent. The tool response alone is not authoritative — a v0.0.5 bug had `update_diagram` reporting success while silently dropping `anchorBindings`. v0.0.6 fixed that occurrence, but keep the round-trip as defense-in-depth. If the diff shows a mismatch, use the direct PUT / direct POST recovery recipes in `references/mcp-tools.md` → "Recovery: write didn't land".
10. **For large SVGs (>~50 KB)** — e.g. after Inkscape text-to-path flattening — skip the `source` parameter in `update_diagram` and direct-PUT the file via `curl -k -X PUT https://<host>:8442/project/<id>/diagram/<file>.svg` instead, then call `update_diagram` only with the metadata you want changed (e.g. `anchorBindings`). Passing 200 KB of SVG through a tool call burns conversation tokens for no benefit. See `references/dashboard-conventions.md` → "Small facts worth knowing" for the exact recipe.

### For "which node type should I use" / "how do I set up X" (non-diagram)
1. Skim `references/node-types/INDEX.md`, narrow to 1–3 candidates by role and one-liner.
2. Read the full JSON spec for each candidate — pay attention to `llmConnectionHints` (what parents/children are valid), `llmSideEffectLevel`, `llmInputs`/`llmOutputs`, and the `llmExamples`.
3. Recommend a single best-fit type with a short rationale. Mention valid parent/child wiring so the user knows where it slots into their tree.
4. Call `create_node` to stand up the node on a server, passing `{server?, type, parent, name?, meta?}`. The `type` accepts either the short name (`KrillApp.DataPoint`) or the FQN. Use `list_node_types` (or the bundled `references/node-types/` specs) to see valid parent/child relationships and the default meta skeleton for each type. For specialized flows — Projects and Diagrams — keep using `create_project` / `create_diagram` (the diagram tool handles the SVG upload + URL computation that `create_node` doesn't).

### For "build this tree on my server" (multi-node authoring)
1. Discover: `list_servers` → `list_nodes` on the chosen server to find the root/parents that already exist.
2. Consult `list_node_types` (or `references/node-types/INDEX.md`) and resolve the user's description to concrete `KrillApp.<Type>` values. Validate each pair against the `validParentTypes` / `validChildTypes` in the registry before building.
3. **Resolve the parent.** First, **mirror the existing tree** — `list_nodes` and look at where DataPoints, Triggers, etc. already sit on this swarm. The server is permissive about parent types (`create_node` warns but doesn't refuse a parent outside `validParentTypes`), so projects routinely organise DataPoints under a `KrillApp.Project` or nest DataPoints under other DataPoints to express a topical hierarchy (`pi-krill-05` does both). When no precedent exists, fall back to the catalog: pick the first type from the target's `validParentTypes` for which the server has exactly one matching node. If none match, walk to the next valid parent type. If multiple match, ask the user which one. Catalog defaults worth memorizing — these are the typical placement, not server-enforced rules:
   - `KrillApp.DataPoint` → typically parented by `KrillApp.Server` (or `KrillApp.Server.SerialDevice` for a sensor wired to a serial device). The server also accepts `KrillApp.Project` (topical grouping) and `KrillApp.DataPoint` (composite readings, e.g. `nitrate` → `nitrate ppm`); mirror the existing tree's pattern when adding new DataPoints rather than "correcting" it.
   - Everything under a Diagram/TaskList/Journal/Camera → parent is the containing `KrillApp.Project`.
   - Triggers → parent is the `KrillApp.DataPoint` they watch (or the shared `KrillApp.Trigger` container under that DataPoint, when one already exists).
   - Executors → parent is the Trigger (or the shared `KrillApp.Executor` container under that Trigger) that fires them.
   - Filters → parent is the `KrillApp.DataPoint.Filter` container under the DataPoint.
4. Build **top-down, parent-first**. Each `create_node` call returns the new `nodeId`; use that as the `parent` for its children. Example chain: `KrillApp.DataPoint` on the server → `KrillApp.Trigger.HighThreshold` on the DataPoint → `KrillApp.Executor.OutgoingWebHook` on the Trigger.
5. Overlay type-specific fields via the `meta` argument — e.g. `{"dataType": "DOUBLE", "unit": "°C", "precision": 1}` for a temperature DataPoint, `{"value": 100.0}` for a HighThreshold. Unknown keys are silently dropped by the server (`ignoreUnknownKeys = true`), so extras are safe but typos go unnoticed — stick to the field names in the MetaData classes.
6. **Verify with `get_node`** after each create. The `create_node` response echoes what was sent, not what persisted; a round-trip read is the only ground truth. The server also **fills in defaults for meta fields you omitted** (e.g. a DataPoint you posted without a `snapshot` comes back with `snapshot: {timestamp: 0, value: ""}`) — not an error, but it's why the GET is authoritative.

### For "natural-language commands → action" (voice or chat-mode requests)

When the user gives an action-shaped request that names a thing in plain language — *"turn on the Vivarium mister"*, *"toggle the porch lamp"*, *"log a temperature reading of 72"* — the goal is to converge in 1–2 round-trips when the swarm is unambiguous and short-circuit to a single confirmation question when it isn't. Voice flows have no inline diff and no rendered tool-call to read; the user said *"turn it on"* and either gets the action or one follow-up question.

1. **Resolve the target.** In current releases, use the shipped MCP tools directly: `list_servers` → for each server, `list_nodes type=<inferred>` → match `displayName` against the user's phrase tokens. Apply this decision rule:
   - One clear best match (for example, an exact/near-exact name match that is clearly better than the others) → use it.
   - Two or more plausible candidates → **ask the user which one**, by server + displayName, never by UUID. Voice example: *"I see two — the Vivarium mister on `pi-krill.local` and the test mister on `pi-krill-05.local`. Which one?"*
   - Empty result → tell the user the phrase didn't match anything in the swarm and stop. Don't speculate-substitute.

   If a future/custom session adds a `find_node(query, type?)` helper, you may use it as an optimization, but do **not** assume it exists in v0.0.8. Any such helper should return ranked `(serverId, nodeId)` candidates, and you should still apply the same disambiguation rule above.

2. **Resolve the action.** "Turn on" / "turn off" / "toggle" maps to firing a `KrillApp.Executor.LogicGate` whose `meta.target` is the named Pin or DataPoint. From the resolved target, look up the controlling gate: walk `meta.parent` upward, or `list_nodes type=LogicGate` on the same server and pick the one whose `meta.target` references the resolved node id. "Log a value" / "record" maps to `record_snapshot` directly on the named DataPoint (covered in the next workflow). "Read" / "what is" maps to `get_node` or `read_series`.

3. **Confirm — based on the *target's* side-effect level, not the executor's.** A LogicGate is `llmSideEffectLevel: "medium"`, but the Pin it writes to is `"high"` — a mister, lamp, valve, or relay is the real-world side effect. Look at the **target** node's catalog `llmSideEffectLevel` (cross-reference `references/node-types/INDEX.md`):
   - **`high`** (Pins, OutgoingWebHook, Lambda, Email/SMS, Camera capture) → **ALWAYS confirm in one short voice-friendly turn.** Include server name + current state so the user can hear what's about to change. Voice example: *"Toggle the Vivarium mister on `pi-krill.local` now? It's currently off."* Wait for an affirmative response before step 4. Do not fire on an ambiguous response — re-ask.
   - **`medium`** → confirm only if step 1 returned multiple close candidates, or if the user's phrase was indirect (*"do the mister thing"* vs *"toggle the mister"*). Otherwise fire directly.
   - **`low`** → fire directly, no confirmation.

4. **Fire.** Prefer `execute_node` when available (tracked as `bsautner/krill-oss#24`; not shipped at v0.0.8). Until it lands, the only way to fire a gate from MCP is to **`record_snapshot` to the gate's `meta.sources[0].nodeId`** with a value that flips its evaluation (DOUBLE: `1.0` / `0.0`; DIGITAL: `1` / `0`; the gate then writes the result to its target Pin). Constraints when using the snapshot fallback:
   - Only after step 3's confirmation has been received.
   - Warn the user **once per session** that this writes a synthetic reading into the source DataPoint's history: *"Heads up — until `execute_node` ships, firing the gate writes a synthetic snapshot into the source DataPoint. The mister will toggle but you'll see an extra reading on the upstream sensor."*
   - If the user says they want to *execute* (not log), and only the snapshot path is available, surface the trade-off and let them decide. Don't silently pollute history.

5. **Report.** After a 1–2 second delay (the gate evaluates async), `get_node` on `meta.targets[0].nodeId` (or the resolved target Pin) and speak the new state — *"Mister on."* / *"Lamp off."* — short enough to fit voice. If the read shows the state didn't change, say so and stop; don't loop.

**Failure modes to avoid in this flow:**
- Silently picking from multiple candidates when scores are close. The whole point of the confirmation budget is to spend one turn on disambiguation rather than fire wrong.
- Firing a `high`-side-effect action without confirmation, even if the phrase is unambiguous. *"Turn on the mister"* is not consent to a specific server's mister.
- Falling back to `record_snapshot` when the user explicitly asked to *execute* something — the snapshot pattern is a stopgap for the missing `execute_node` tool, not a voice answer. Always surface the trade-off.
- Multi-turn breadth-first exploration (`list_servers` → walk every server → ask the user to clarify the intent). Voice has no budget for it; resolve in one `find_node` call or escalate to a single confirmation question.

### For "record values to a DataPoint" (single value or a backfill series)
1. Get the target DataPoint id (via `list_nodes type=DataPoint` or straight from the user).
2. Call `record_snapshot` with either `{dataPointId, value, timestamp?}` for a single reading or `{dataPointId, snapshots: [{timestamp, value}, ...]}` for a series. `timestamp` is epoch **milliseconds**. **If MCP tools aren't in-session**, snapshot writes have **no Krill REST endpoint** — don't probe `/data`, `/snapshot`, `/data/record`, etc. on `:8442` (they 404). Use the **Direct-to-MCP JSON-RPC fallback** in `references/mcp-tools.md` to reach `record_snapshot` through `http://<host>:50052/mcp`.
3. Values are validated client-side against the DataPoint's `dataType`: TEXT non-empty, DIGITAL ∈ {0, 1} (booleans auto-map to 0/1), DOUBLE parseable, JSON non-empty. **COLOR values are the decimal string of a 24-bit RGB integer** — `(R<<16)|(G<<8)|B`, each channel 0–255, no alpha. Examples: red `"16711680"` (0xFF0000), yellow `"11778048"` (0xB3B800), white `"16777215"`, black `"0"`. Don't pass hex strings like `"#B3B800"` or CSS names — they'll fail validation. Alpha is never stored; the Krill client reconstitutes opaque alpha at render time. **When in doubt, mirror the existing value** — `get_node` on a COLOR DataPoint and copy whatever string you see in `meta.snapshot.value`. If validation fails for any snapshot in a batch, **nothing is posted** — the tool refuses to half-apply a series.
4. Each POST returns 202 Accepted before the server finishes ingesting. Two rules for verifying with `read_series`:
   - **Wait ~1.5 seconds before the first `read_series` call**, or be prepared to retry once. The ingest pipeline (`scope.launch` on the server) commonly returns 0 snapshots on an immediate follow-up read even on bare DataPoints with no filter children — a short delay or one retry reliably recovers. Don't interpret an empty first read as "the write failed" unless the retry also returns empty.
   - **Persistence-verification is mandatory** when the DataPoint has a `DiscardAbove` / `DiscardBelow` / `Deadband` / `Debounce` filter child — those can silently drop a snapshot for good. On a bare DataPoint, verification is optional but still a useful round-trip.

## Topology, auth, limits

- **Two ports, two roles.** `krill-mcp` runs on `:50052/mcp` over plain **HTTP**. The underlying `krill` server runs on `:8442` over **HTTPS** with a self-signed cert. Both accept the **same** PIN-derived bearer — so when `krill-mcp` is wedged, you can fall back to `curl -sk https://<host>:8442/...` with the same token.
- **Where to find the bearer token** (in order of convenience):
  1. On the krill-mcp host: `sudo krill-mcp-token` prints the connector URL + bearer.
  2. On any machine with `krill` installed: `~/.krill/pin_token` holds the 64-char hex bearer. Use as-is.
  3. On a server: `/etc/krill/credentials/pin_derived_key` (mode 0400, owned by `krill:krill`).
- **v0.0.8 write surface covers any node type, DataPoint value writes, and delete.** Project/Diagram helpers: `create_project`, `list_projects`, `create_diagram`, `update_diagram`, `get_diagram`, `upload_diagram_file`, `download_diagram_file`. Generic node authoring: `create_node`, `list_node_types`. DataPoint time-series writes: `record_snapshot`. Cascading delete: `delete_node` (deletes a Project recursively removes Diagrams/TaskLists/Journals/Cameras; deleting a DataPoint recursively removes its Filters/Triggers/Executors/Graphs).
- **Diagrams must live under a Project.** Never call `create_diagram` with a `projectId` you haven't verified exists — call `list_projects` first, or create one with `create_project` and reuse the returned id.
- **Registry is seed-config-driven and bootstrap-only.** There is **no** `add_server` MCP tool. To add a Krill server to an MCP install, edit `/etc/krill-mcp/config.json`'s `seeds` array and `sudo systemctl restart krill-mcp`. The `server` argument on tool calls only resolves servers already in the registry — passing a new hostname won't register it.
- **Auth is one shared bearer token** per swarm. Anyone holding it can read everything AND write Diagrams; treat it like a household password.
- **Self-signed TLS to Krill.** The MCP daemon (and your fallback `curl -k`) trusts any cert presented by Krill; security comes from the bearer token. Don't suggest TLS-pinning workarounds.
- **Side-effect levels matter.** Before recommending an `executor` or `action` node, look at `llmSideEffectLevel` in its JSON — `high` means real-world side effects (sends mail, hits webhooks, runs Python, controls hardware). Make the user explicitly confirm before proposing those.

## House style

- Be concrete: when explaining a node, cite its full `KrillApp.<Type>` name and link it back to its catalog entry.
- Treat node UUIDs as opaque — show them when needed, never invent them.
- Prefer a small number of well-explained nodes over a sprawling tree. Krill is meant to be readable.
