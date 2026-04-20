---
name: krill
version: 0.0.3
description: Use when working with a Krill swarm — the home-automation/IoT system whose nodes are reachable via the krill-mcp Model Context Protocol server (typically http://<host>:50052/mcp, see https://krillswarm.com). Invoke for discovering or inspecting Krill servers, nodes, DataPoints, Triggers, Filters, Executors, Pins, or peers; reading time-series sensor data; reasoning about which node type to use for a given automation; and authoring SVG dashboards (Diagram nodes) that overlay live node state on a custom layout. Triggers on keywords like krill, swarm, krill server, krill node, krill-mcp, DataPoint, Trigger threshold, SVG dashboard, k_ anchor, swarm sensor, pi-krill.
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

- **`references/mcp-tools.md`** — the 5 MCP tools (`list_servers`, `list_nodes`, `get_node`, `read_series`, `server_health`), their JSON shapes, the standard discovery flow, auth setup. Read this first whenever you need to query a swarm.
- **`references/node-types/INDEX.md`** — table of all 37 Krill node types grouped by role (state / trigger / action / filter / display / container / infra) with one-liners and side-effect levels. Read this when the user's request needs a node type and you don't already know which one applies.
- **`references/node-types/KrillApp.<Type>.json`** — full spec for one node type: purpose, behavior, inputs, outputs, valid parents/children, side-effect level, examples. Read after narrowing via the index, before recommending the type to the user.
- **`references/dashboard-conventions.md`** — the `k_*` anchor convention, `DiagramMetaData` shape, authoring guidance, reference example. Read before generating any SVG dashboard.

## Standard workflow

### For a discovery / inspection request
1. If MCP tools are available in this Claude session, call `list_servers` → `server_health` → `list_nodes` (with a `type` filter when the user's intent narrows it). Use `get_node` for specific nodes the user names. Use `read_series` only when historical values matter.
2. If MCP tools are not configured, fall back to a raw `curl` against `http://<host>:50052/mcp` with the bearer token, and tell the user how to add the Custom Connector so it works natively next time.
3. Translate type strings like `krill.zone.shared.KrillApp.DataPoint` into the human catalog entry from `references/node-types/INDEX.md` when explaining results.

### For "which node type should I use" / "how do I set up X"
1. Skim `references/node-types/INDEX.md`, narrow to 1–3 candidates by role and one-liner.
2. Read the full JSON spec for each candidate — pay attention to `llmConnectionHints` (what parents/children are valid), `llmSideEffectLevel`, `llmInputs`/`llmOutputs`, and the `llmExamples`.
3. Recommend a single best-fit type with a short rationale. Mention valid parent/child wiring so the user knows where it slots into their tree.
4. If the answer involves writing/creating nodes, note that krill-mcp v0.0.3 is **read-only** — give the configuration as JSON / instructions for the user to apply in the Krill app, and flag that this will become end-to-end automatic when write tools land.

### For SVG dashboard requests
1. Call `list_nodes` (filter `type=DataPoint`) so you know what live values exist on the target server. Capture each DataPoint's id, name, dataType, and unit.
2. Read `references/dashboard-conventions.md` — anchor naming (`k_*`), `DiagramMetaData.anchorBindings: Map<String,String>`, runtime behavior.
3. Ask the user (or infer) the layout: floor plan, equipment diagram, grid of tiles, themed illustration. If they give you a rough sketch or list of regions, use it. Default to a clean dark-background tile grid if no preference.
4. Emit two artifacts:
   - The SVG, with one element per shown DataPoint, each `id="k_<descriptive-snake-name>"` matching the DataPoint name.
   - A JSON `anchorBindings` map: `{"k_<name>": "<datapoint-uuid>", ...}`.
5. Tell the user how to apply: create a `KrillApp.Project.Diagram` node under one of their `KrillApp.Project` containers, paste the SVG into `source`, paste the binding map into `anchorBindings`. Don't promise the skill did the install — write tools don't exist yet.

## Capabilities and limits

- **Read-only today.** krill-mcp v0.0.3 exposes only `list_servers`, `list_nodes`, `get_node`, `read_series`, `server_health`. No node creation, mutation, execution, or camera capture.
- **Auth is one shared bearer token** per swarm — the PIN-derived key. Anyone holding it can read everything; treat it like a household password, not a per-user credential.
- **Self-signed TLS to Krill.** The MCP daemon trusts any cert presented by Krill; security comes from the bearer token. Don't suggest TLS-pinning workarounds.
- **Single MCP per household.** One krill-mcp instance proxies to N Krill servers listed in `/etc/krill-mcp/config.json` `seeds`. Adding a server means editing that config and restarting the service.
- **Side-effect levels matter.** Before recommending an `executor` or `action` node, look at `llmSideEffectLevel` in its JSON — `high` means real-world side effects (sends mail, hits webhooks, runs Python, controls hardware). Make the user explicitly confirm before proposing those.

## House style

- Be concrete: when explaining a node, cite its full `KrillApp.<Type>` name and link it back to its catalog entry.
- Treat node UUIDs as opaque — show them when needed, never invent them.
- Prefer a small number of well-explained nodes over a sprawling tree. Krill is meant to be readable.
