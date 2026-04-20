# SVG dashboard conventions

Krill renders an interactive dashboard from a static SVG file plus a binding map. The SVG is authored normally (Inkscape, Figma, or hand-edited) — Krill only cares about elements whose `id` starts with `k_`. Those are **anchors** that get bound to live nodes at runtime.

## The contract

1. **Author the SVG.** Any element with `id="k_<descriptive-name>"` is treated as an anchor. Common patterns:
   - `<rect id="k_aquarium_level_sensor" .../>` — a region that will display a node icon and live value
   - `<g id="k_pump_relay">...</g>` — a group the runtime will swap based on node state
   - The descriptive name after `k_` is for human readability; the binding to an actual node is set separately.

2. **Author the bindings.** A `KrillApp.Project.Diagram` node holds the SVG content plus an `anchorBindings: Map<String, String>` that maps each anchor id (without the `k_` prefix? — verify in `DiagramMetaData.kt`; the field key is the SVG element id as-is including `k_`) to a node UUID:
   ```json
   {
     "name": "Aquarium dashboard",
     "source": "<svg>...</svg>",
     "anchorBindings": {
       "k_aquarium_level_sensor": "1c9dce76-ba65-48b5-b842-32ad97a96f80",
       "k_ammonia_ppm":           "65691be2-f712-4727-8125-c94b66b3820e"
     }
   }
   ```

3. **At render time** the Krill client subscribes to each bound DataPoint's state flow and updates the corresponding anchor's icon, color, and/or text in real time. No animation system, no scripting in the SVG itself — just live state replacement.

## Authoring guidance

- **Pick anchor names that match real DataPoint names.** Even though the binding is by UUID, descriptive ids make the dashboard self-documenting and let the user (or Claude) regenerate bindings without guessing.
- **Anchor element type matters less than position and size.** A `<rect>` is the safest container — the renderer overlays the live indicator on top. Use `fill="none"` and a faint stroke if you want the rect itself invisible.
- **Layered SVGs are fine.** Inkscape layers (`<g inkscape:groupmode="layer">`) survive the round-trip; just keep the `k_*` anchors at the layer level Krill will scan (top-level under `<svg>` or nested in groups — both work).
- **Background art and decoration** can be anything: drop in a floor plan, an aquarium illustration, a process diagram. The renderer doesn't transform non-anchor elements.
- **Keep the file small.** Inkscape's verbose XML inflates files quickly (the bundled `water_demo.svg` is ~22k lines). Run through `svgo` before shipping if size matters.

## Reference example

`water_demo.svg` (in the closed `krill` repo at `shared/src/commonMain/resources/`) is the canonical example — an aquarium dashboard with anchors like `k_aquarium_level_sensor`, drawn in Inkscape. Worth opening to see what real anchors look like in the wild.

## Workflow when generating a dashboard for a user

1. Call `list_nodes` (filtered by `type=DataPoint`) to know what live values exist.
2. Ask the user what physical/conceptual layout they want, or generate a sensible default (grid of tiles, floor-plan rectangles, equipment diagram).
3. Emit the SVG with descriptive `k_*` anchor ids — one anchor per DataPoint the dashboard should show.
4. Emit the matching `anchorBindings` JSON, mapping each anchor id to the DataPoint UUID from step 1.
5. Tell the user to: create a `KrillApp.Project.Diagram` node under one of their `KrillApp.Project` containers, paste the SVG into the `source` field, and paste the binding map into `anchorBindings`. (Or, once write tools land in krill-mcp, do all of this via `tools/call`.)

## Don't

- Don't put `k_*` ids on elements you don't want bound — every `k_*` anchor will get an indicator overlaid on it.
- Don't rely on SMIL, CSS animations, or `<script>` inside the SVG to carry data — the renderer only swaps state at anchor points.
- Don't use the same `k_*` id twice in one file.
