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

The `create_diagram` MCP tool encapsulates both steps: pass the SVG content as `svg` and the tool uploads, then posts the node. If you're doing it by hand (curl fallback, direct-to-Krill), you PUT then POST in that order.

## The contract

1. **Author the SVG.** Any element with `id="k_<descriptive-name>"` is treated as an anchor. Common patterns:
   - `<rect id="k_aquarium_level_sensor" .../>` — a region that will display a node icon and live value
   - `<g id="k_pump_relay">...</g>` — a group the runtime will swap based on node state
   - The descriptive name after `k_` is for human readability; the binding to an actual node is set separately.

2. **Author the bindings.** `DiagramMetaData.anchorBindings: Map<String, String>` maps each SVG anchor id (as-is, including the `k_` prefix) to a node UUID:
   ```json
   {
     "name": "Aquarium dashboard",
     "source": "https://pi-krill-05.local:8442/project/1a2b.../diagram/aquarium_dashboard.svg",
     "anchorBindings": {
       "k_aquarium_level_sensor": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
       "k_ammonia_ppm":           "65691be2-f712-4727-8125-c94b66b3820e"
     }
   }
   ```

3. **At render time** the Krill client loads the SVG from `source`, then subscribes to each bound DataPoint's state flow and updates the corresponding anchor's icon, color, and/or text in real time. No animation system, no scripting in the SVG itself — just live state replacement.

## Authoring guidance

- **Pick anchor names that match real DataPoint names.** Even though the binding is by UUID, descriptive ids make the dashboard self-documenting and let the user (or Claude) regenerate bindings without guessing.
- **Anchor element type matters less than position and size.** A `<rect>` is the safest container — the renderer overlays the live indicator on top. Use `fill="none"` and a faint stroke if you want the rect itself invisible.
- **Layered SVGs are fine.** Inkscape layers (`<g inkscape:groupmode="layer">`) survive the round-trip; just keep the `k_*` anchors at the layer level Krill will scan (top-level under `<svg>` or nested in groups — both work).
- **Background art and decoration** can be anything: drop in a floor plan, an aquarium illustration, a process diagram. The renderer doesn't transform non-anchor elements.
- **Keep the file small.** Inkscape's verbose XML inflates files quickly (the bundled `water_demo.svg` is ~22k lines). Run through `svgo` before shipping if size matters. The server enforces a 2 MB upload cap.

## Reference example

`water_demo.svg` (in the closed `krill` repo at `shared/src/commonMain/resources/`) is the canonical example — an aquarium dashboard with anchors like `k_aquarium_level_sensor`, drawn in Inkscape. Worth opening to see what real anchors look like in the wild.

## Workflow when generating a dashboard for a user

1. Call `list_nodes` (filtered by `type=DataPoint`) to know what live values exist.
2. Call `list_projects` to find a parent project. If none exist, offer to create one with `create_project` — diagrams **must** be children of a project. If exactly one project exists, use it; if several, ask which.
3. Ask the user what physical/conceptual layout they want, or generate a sensible default (grid of tiles, floor-plan rectangles, equipment diagram).
4. **Stage the SVG on disk first.** Write the generated markup to `/tmp/<slug>.svg` (where `<slug>` is the lowercase snake-case of the diagram name). A real file lets you re-read, diff, and iterate with ordinary file tools before uploading. Keep using that same tmp file across iterations so you never lose intermediate work.
5. Build the `anchorBindings` map (anchor id → DataPoint UUID from step 1).
6. Call `create_diagram` with `{projectId, name, svg: <tmp file contents>, anchorBindings}`. The tool auto-picks a filename (`slug(name) + ".svg"`) unless you pass `fileName`. It uploads the file and posts the node — `meta.source` comes back as the full `https://…/project/{id}/diagram/{file}` URL.

Do **not** try to set `meta.source` to inline SVG markup. It's a URL. The server won't render an inline fragment and the app will show a broken diagram.

## Workflow for improving an existing dashboard

1. Call `get_diagram` with the diagram id. The response includes both the `source` URL and the fetched `svg` content so you can reason about the current markup directly.
2. Save the returned `svg` to `/tmp/<slug>.svg` and edit it there — layout tweaks, new anchors for newly-created DataPoints, visual polish, svg optimization. Diff against the original tmp snapshot to see exactly what changed.
3. Call `update_diagram` with `{diagramId, svg: <new markup>, anchorBindings?}`. By default the tool re-uploads to the same filename referenced by the current `source` URL, so the URL stays stable and any existing embeds keep working. Pass `fileName` only when you need to rename the file.
4. **Preserve anchor ids that are still in use.** Renaming an anchor (e.g. `k_temp_1` → `k_temperature`) without also updating its binding breaks the live-state overlay. If you must rename, rewrite `anchorBindings` in the same `update_diagram` call.

## Don't

- Don't put the SVG markup in `meta.source` — it's a URL.
- Don't put `k_*` ids on elements you don't want bound — every `k_*` anchor will get an indicator overlaid on it.
- Don't rely on SMIL, CSS animations, or `<script>` inside the SVG to carry data — the renderer only swaps state at anchor points.
- Don't use the same `k_*` id twice in one file.
- Don't hand-write the `source` URL with the wrong host — let `create_diagram` fill it from the server's own `/health` metadata so mobile/desktop clients can resolve it.
