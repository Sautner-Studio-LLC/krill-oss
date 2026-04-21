---
name: krill
version: 0.0.5
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

## Bundled references

Read these on demand — they're not auto-loaded:

- **`references/mcp-tools.md`** — the MCP tools (read: `list_servers`, `list_nodes`, `get_node`, `read_series`, `server_health`; write: `list_projects`, `create_project`, `create_diagram`, `update_diagram`, `get_diagram`, `upload_diagram_file`, `download_diagram_file`), their JSON shapes, the standard discovery + diagram flows, auth setup. Read this first whenever you need to query or mutate a swarm.
- **`references/node-types/INDEX.md`** — table of all 37 Krill node types grouped by role (state / trigger / action / filter / display / container / infra) with one-liners and side-effect levels. Read this when the user's request needs a node type and you don't already know which one applies.
- **`references/node-types/KrillApp.<Type>.json`** — full spec for one node type: purpose, behavior, inputs, outputs, valid parents/children, side-effect level, examples. Read after narrowing via the index, before recommending the type to the user.
- **`references/dashboard-conventions.md`** — the `k_*` anchor convention, `DiagramMetaData` shape, authoring guidance, reference example. Read before generating any SVG dashboard.

## Standard workflow

### For a discovery / inspection request
1. If MCP tools are available in this Claude session, call `list_servers` → `server_health` → `list_nodes` (with a `type` filter when the user's intent narrows it). Use `get_node` for specific nodes the user names. Use `read_series` only when historical values matter.
2. **If `list_servers` returns `{"servers": []}`** — the registry is empty, almost always a known startup seed race (`krill-mcp` came up before the `krill` server it probes). Read `references/mcp-tools.md` → "Troubleshooting" for the journalctl diagnostic and `sudo systemctl restart krill-mcp` fix. Don't restart the user's service without asking; meanwhile, consider the direct-to-krill fallback below.
3. **If the user names a Krill server (e.g. "check pi-krill-05") and it's not in `list_servers`** — before giving up, try `list_nodes` with `server: "localhost"` (or look in `list_servers` for a `localhost`-hosted entry). The default seed is `localhost:8442`, so a krill-mcp co-installed with a `krill` server registers the host under that alias — the user may have named the box thinking of it as the Krill server while the MCP only knows it as `localhost`. If that succeeds, tell the user you resolved their hostname to the MCP's local entry so they're not surprised.
4. **If MCP tools are not configured** in the session, fall back to raw `curl` against `http://<host>:50052/mcp` with the bearer token, and tell the user how to add the Custom Connector so it works natively next time.
5. **If `krill-mcp` is wedged** (empty registry, stopped, or unreachable), fall back to hitting the Krill server's REST API directly with the same bearer — `curl -sk -H "Authorization: Bearer $TOKEN" https://<host>:8442/nodes`. Self-signed TLS, same routes as the MCP tools wrap. Details in `references/mcp-tools.md` → "Direct-to-Krill fallback".
6. Translate type strings like `krill.zone.shared.KrillApp.DataPoint` into the human catalog entry from `references/node-types/INDEX.md` when explaining results.

### For SVG dashboard requests (create / improve)

**Key invariant:** `DiagramMetaData.source` is a **URL**, not inline SVG. Krill stores the SVG as a file on the server and renders it by fetching that URL. The `create_diagram` and `update_diagram` tools handle the full two-step (upload file → post node with URL) — don't try to cram SVG markup into `source` yourself.

1. Call `list_nodes` (filter `type=DataPoint`) so you know what live values exist on the target server. Capture each DataPoint's id, name, dataType, and unit.
2. Read `references/dashboard-conventions.md` — anchor naming (`k_*`), `DiagramMetaData.anchorBindings: Map<String,String>`, the URL-not-inline contract, runtime behavior.
3. **Pick the parent project.** Diagrams must be children of a `KrillApp.Project` container.
   - Call `list_projects` first. If exactly one project exists, use it (announce the choice). If several, ask the user which one. If none exist, offer to create one and call `create_project` with a sensible name.
4. Ask the user (or infer) the layout: floor plan, equipment diagram, grid of tiles, themed illustration. If they give you a rough sketch or list of regions, use it. Default to a clean dark-background tile grid if no preference.
5. **Stage the SVG in `/tmp/<slug>.svg`** where `<slug>` is the lowercase_snake_case of the diagram name. Writing a real file first makes it easy to re-read, diff across iterations, and hand-inspect before shipping — nothing is committed until you call `create_diagram`. Use the same tmp path across edits so intermediate work isn't lost.
6. Build the SVG with one element per shown DataPoint, each `id="k_<descriptive_snake_name>"`, and the matching `anchorBindings` map `{"k_<name>": "<datapoint-uuid>", ...}`.
7. Call `create_diagram` with `{projectId, name, svg: <contents of /tmp/<slug>.svg>, anchorBindings}`. The tool uploads the file, builds the public URL, and posts the node in one step. Filename defaults to `slug(name) + ".svg"`; override with `fileName` only when you need something non-derivable.
8. For **improving an existing diagram**: call `get_diagram` — the response includes both the `source` URL and the fetched `svg` content. Save it to `/tmp/<slug>.svg`, edit there, reason about what to change (layout tweak, new anchors for newly-created DataPoints, visual cleanup), then call `update_diagram` with the new `svg` and/or `anchorBindings`. Preserve anchor ids that are still in use — changing them without rewriting `anchorBindings` breaks the live overlay.

### For "which node type should I use" / "how do I set up X" (non-diagram)
1. Skim `references/node-types/INDEX.md`, narrow to 1–3 candidates by role and one-liner.
2. Read the full JSON spec for each candidate — pay attention to `llmConnectionHints` (what parents/children are valid), `llmSideEffectLevel`, `llmInputs`/`llmOutputs`, and the `llmExamples`.
3. Recommend a single best-fit type with a short rationale. Mention valid parent/child wiring so the user knows where it slots into their tree.
4. The only write tools today are for **Projects and Diagrams** (`create_project`, `create_diagram`, `update_diagram`, `upload_diagram_file`). For any other node type, emit the configuration as JSON / instructions for the user to apply in the Krill app, and flag that broader write support is a future capability.

## Topology, auth, limits

- **Two ports, two roles.** `krill-mcp` runs on `:50052/mcp` over plain **HTTP**. The underlying `krill` server runs on `:8442` over **HTTPS** with a self-signed cert. Both accept the **same** PIN-derived bearer — so when `krill-mcp` is wedged, you can fall back to `curl -sk https://<host>:8442/...` with the same token.
- **Where to find the bearer token** (in order of convenience):
  1. On the krill-mcp host: `sudo krill-mcp-token` prints the connector URL + bearer.
  2. On any machine with `krill` installed: `~/.krill/pin_token` holds the 64-char hex bearer. Use as-is.
  3. On a server: `/etc/krill/credentials/pin_derived_key` (mode 0400, owned by `krill:krill`).
- **v0.0.5 write surface is Projects + Diagrams only.** Tools: `create_project`, `list_projects`, `create_diagram`, `update_diagram`, `get_diagram`, `upload_diagram_file`, `download_diagram_file`. Everything else (DataPoints, Triggers, Executors, Filters, Pins, etc.) is still read-only.
- **Diagrams must live under a Project.** Never call `create_diagram` with a `projectId` you haven't verified exists — call `list_projects` first, or create one with `create_project` and reuse the returned id.
- **Registry is seed-config-driven and bootstrap-only.** There is **no** `add_server` MCP tool. To add a Krill server to an MCP install, edit `/etc/krill-mcp/config.json`'s `seeds` array and `sudo systemctl restart krill-mcp`. The `server` argument on tool calls only resolves servers already in the registry — passing a new hostname won't register it.
- **Auth is one shared bearer token** per swarm. Anyone holding it can read everything AND write Diagrams; treat it like a household password.
- **Self-signed TLS to Krill.** The MCP daemon (and your fallback `curl -k`) trusts any cert presented by Krill; security comes from the bearer token. Don't suggest TLS-pinning workarounds.
- **Side-effect levels matter.** Before recommending an `executor` or `action` node, look at `llmSideEffectLevel` in its JSON — `high` means real-world side effects (sends mail, hits webhooks, runs Python, controls hardware). Make the user explicitly confirm before proposing those.

## House style

- Be concrete: when explaining a node, cite its full `KrillApp.<Type>` name and link it back to its catalog entry.
- Treat node UUIDs as opaque — show them when needed, never invent them.
- Prefer a small number of well-explained nodes over a sprawling tree. Krill is meant to be readable.
