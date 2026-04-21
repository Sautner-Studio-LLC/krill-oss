# SVG dashboard conventions

Krill renders an interactive dashboard from a static SVG file plus a binding map. The SVG is authored normally (Inkscape, Figma, or hand-edited) — Krill only cares about elements whose `id` starts with `k_`. Those are **anchors** that get bound to live nodes at runtime.

## How Krill stores a Diagram — read this first

`KrillApp.Project.Diagram` is a node whose `meta.source` is **a URL, not inline SVG**. The SVG itself lives as a file on the Krill server, at `/srv/krill/project/{projectId}/diagram/{filename}.svg`, and is served via:

```
https://<krill-host>:8442/project/{projectId}/diagram/{filename}.svg
```

So the full create flow is:

1. **PUT the SVG bytes** to `/project/{projectId}/diagram/{filename}.svg` with `Content-Type: image/svg+xml`. The server writes the file with the correct owner (`krill`) and permissions — you do **not** shell into the box and `sudo mv` anything.
2. **POST the Diagram node** with `meta.source` set to the public URL of that file (same `host:port` the Krill app uses to talk to the server), and `meta.anchorBindings` set to the anchor → nodeId map.

The `create_diagram` MCP tool encapsulates both steps: pass the SVG content as `source` and the target filename as `uploadFileName`, and the tool PUTs the file then posts the node. Always pass `uploadFileName` — without it the file isn't uploaded and `meta.source` can't resolve. If you're doing it by hand (curl fallback, direct-to-Krill), you PUT then POST in that order.

## The contract

1. **Author the SVG.** Anchors are **plain, empty, transparent `<rect>` elements** whose id is `k_<node-uuid>` — the literal `k_` prefix followed by the bound node's UUID:
   ```xml
   <rect id="k_1c9dce76-ba65-48b5-b842-32ad97a96f80"
         x="30" y="30" width="280" height="140"
         fill="none"/>
   ```
   The rect is a **positional placeholder only** — it defines where and how big the node's live UI should appear. Do **not** draw anything inside or on top of the anchor: no `<text>` label, no numeric readout, no placeholder chart, no icon, no child elements. The Krill client overlays the full live rendering (icon, title, value, graph, trigger indicator, etc.) at that rect's bounding box at runtime — anything you author inside gets double-rendered.

2. **Author the bindings.** `DiagramMetaData.anchorBindings: Map<String, String>` maps each `k_<uuid>` anchor id to that same `<uuid>`. The binding is semantically redundant with the id but is still required — the runtime reads it to know which anchors are wired:
   ```json
   {
     "name": "Project overview",
     "source": "https://pi-krill-05.local:8442/project/1a2b.../diagram/project_overview.svg",
     "anchorBindings": {
       "k_1c9dce76-ba65-48b5-b842-32ad97a96f80": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
       "k_65691be2-f712-4727-8125-c94b66b3820e": "65691be2-f712-4727-8125-c94b66b3820e"
     }
   }
   ```
   Do not use descriptive slugs like `k_ammonia_ppm` — the id must encode the UUID.

3. **At render time** the Krill client loads the SVG from `source`, finds each `k_*` anchor, and renders the bound node's full live UI (icon + title + value + graph as appropriate for the node type) inside the anchor's bounding box. The underlying SVG rect itself is invisible — it just reserves the region. No animation system, no scripting in the SVG itself.

## Authoring guidance

- **You're designing a layout, not a visual.** Ask the user (or infer) where each node's tile/region should sit and how big — that's the entire creative surface. The Krill client fills in every pixel.
- **Anchors must be `<rect fill="none"/>` with no children.** `<g>` groups that wrap an anchor are fine as layout scaffolding, but the id-carrying element should itself be an empty rect. Don't put decorative strokes on the anchor rect — the overlay will sit on top and the stroke will bleed through.
- **Background art and decoration** outside of anchors can be anything that doesn't have a `k_*` id: a floor plan, an aquarium illustration, section dividers, a dark panel background. The renderer doesn't transform non-anchor elements, so decoration survives as-is.
- **Static text rarely makes sense.** Section headers or captions *do* render (as whatever SVG you author), but Krill's renderer has historically skipped raw `<text>` elements in some client versions — if you need static labels, convert them to paths via `inkscape input.svg --export-text-to-path --export-plain-svg --export-filename=out.svg`. In practice, since the live overlays include the node title, you almost never need your own labels.
- **Layered SVGs are fine.** Inkscape layers (`<g inkscape:groupmode="layer">`) survive the round-trip; just keep the `k_*` anchors at the layer level Krill will scan (top-level under `<svg>` or nested in groups — both work).
- **Keep the file small.** Inkscape's verbose XML inflates files quickly (the bundled `water_demo.svg` is ~22k lines). Run through `svgo` before shipping if size matters. The server enforces a 2 MB upload cap.

## Reference example

`water_demo.svg` (in the closed `krill` repo at `shared/src/commonMain/resources/`) is the canonical example — an aquarium dashboard drawn in Inkscape. Worth opening to see the Inkscape-authored layering structure in the wild. Note: that file predates the current `k_<node-uuid>` anchor convention and uses descriptive slug ids (e.g. `k_aquarium_level_sensor`); new dashboards should use UUID-encoded ids instead.

## Workflow when generating a dashboard for a user

1. Call `list_nodes` to enumerate the nodes the user will want to surface (DataPoints, Graphs, Triggers, Executors — whatever lives under the target project).
2. Call `list_projects` to find a parent project. If none exist, offer to create one with `create_project` — diagrams **must** be children of a project. If exactly one project exists, use it; if several, ask which.
3. Ask the user what physical/conceptual layout they want, or generate a sensible default (grid of tiles, floor-plan rectangles, equipment diagram). Remember: you're only deciding positions and sizes; the client renders everything else.
4. **Stage the SVG on disk first.** Write the generated markup to `/tmp/<slug>.svg` (where `<slug>` is the lowercase snake-case of the diagram name). A real file lets you re-read, diff, and iterate with ordinary file tools before uploading. Keep using that same tmp file across iterations so you never lose intermediate work.
5. Emit one `<rect id="k_<node-uuid>" x=".." y=".." width=".." height=".." fill="none"/>` per bound node. No inner content, no decoration *on* the rect.
6. Build `anchorBindings` as `{"k_<uuid>": "<uuid>", ...}` — each anchor id maps to the UUID baked into it.
7. Call `create_diagram` with `{projectId, name, source: <tmp file contents>, anchorBindings}`. The tool **always** uploads the SVG and constructs `meta.source` — filename defaults to `slug(name)+".svg"`. Pass `uploadFileName` only if you need a specific filename different from that default; upload happens either way.

Do **not** try to set `meta.source` to inline SVG markup. It's a URL. The server won't render an inline fragment and the app will show a broken diagram.

## Workflow for improving an existing dashboard

1. Call `get_diagram` with the diagram id. The response includes both the `source` URL and the fetched `svg` content so you can reason about the current markup directly. **Save the returned `svg` to `/tmp/<slug>_before.svg`** — this is your baseline for the verification step at the end.
2. Write your edits to `/tmp/<slug>.svg` — layout tweaks, new anchors for newly-created DataPoints, visual polish, svg optimization. Diff `/tmp/<slug>.svg` against `/tmp/<slug>_before.svg` to see exactly what changed.
3. Call `update_diagram` with `{diagramId, source: <new markup>, anchorBindings?}`. When `source` is given, the tool **always** re-uploads — by default to the same filename the current `meta.source` URL references, so the URL stays stable and any existing embeds keep working. Pass `uploadFileName` only to rename the file. The response echoes the post-update `anchorBindings` and `anchorCount`.
4. **Round-trip verify the write.** Call `get_diagram` again and compare its `svg` + `anchorBindings` against what you just sent. If anything differs, the write didn't fully land — fall back to the direct-PUT / direct-POST recovery recipes in `mcp-tools.md` → "Recovery: write didn't land". This defense-in-depth step caught a silent no-op bug in v0.0.5; keep doing it even when the tool response looks clean.
5. **Preserve anchor ids that are still in use.** Since every anchor id is `k_<node-uuid>`, the only legitimate reason to change one is if the bound node itself was replaced — in which case you must rewrite `anchorBindings` in the same `update_diagram` call so the new id maps to the new UUID. Removing an anchor whose node is gone is fine; renaming an anchor while keeping the same binding is never correct under this convention.

## Small facts worth knowing

- **UUIDs with hyphens are legal SVG ids.** `id="k_1c9dce76-ba65-48b5-b842-32ad97a96f80"` is valid XML, valid SVG, and a valid CSS selector. No escaping, no underscore-swap, no quoting — emit the UUID as-is.
- **Over ~50 KB, prefer the direct-PUT route instead of `update_diagram` source.** Large SVGs (especially after Inkscape text-to-path flattening — a 20-element text run became ~220 KB / 519 paths in one test) bloat the tool-call payload on the way in and on the way out, burning conversation tokens for no benefit. Script a shell step instead:
  ```bash
  TOKEN=$(cat ~/.krill/pin_token)
  curl -sk -X PUT \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: image/svg+xml" \
    --data-binary @/tmp/<slug>.svg \
    "https://<host>:8442/project/<projectId>/diagram/<filename>.svg"
  ```
  Then call `update_diagram` with **no `source` field** — only the metadata you want to change (e.g. `anchorBindings`). The file is already fresh at the same URL.
- **Inkscape text-to-path fallback.** Some client versions historically skipped raw `<text>` elements. If you inherited a diagram with static labels and need them rendered, flatten to paths:
  ```bash
  inkscape input.svg --export-text-to-path --export-plain-svg --export-filename=output.svg
  ```
  Preserves all `k_*` ids; file size grows (8 KB → 220 KB in one test — still under the 2 MB cap). Since the Krill client overlays live UI including titles on top of every anchor, you rarely want authored labels in the first place.
- **`/tmp/<slug>.svg` staging is how you spot silent write failures.** Keep the pre-edit snapshot (`/tmp/<slug>_before.svg`) alongside the edit so a `diff` against a post-write `get_diagram` result tells you immediately whether the write landed.

## Don't

- Don't put the SVG markup in `meta.source` — it's a URL.
- Don't put `k_*` ids on elements you don't want bound — every `k_*` anchor will get an indicator overlaid on it.
- Don't rely on SMIL, CSS animations, or `<script>` inside the SVG to carry data — the renderer only swaps state at anchor points.
- Don't use the same `k_*` id twice in one file.
- Don't hand-write the `source` URL with the wrong host — let `create_diagram` fill it from the server's own `/health` metadata so mobile/desktop clients can resolve it.
