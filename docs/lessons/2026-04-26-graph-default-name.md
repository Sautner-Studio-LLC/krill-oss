# Graph default name collided across siblings

**Issue:** [krill-oss#13](https://github.com/bsautner/krill-oss/issues/13)
**Root cause category:** API design — colliding default value
**Module:** `krill-sdk`, `krill-mcp`

## What happened

`GraphMetaData(name = "Data Graph", …)` made every Graph node created without
an explicit `name` indistinguishable from its siblings. A real swarm
(`pi-krill-05`) accumulated three Graphs all named `"Data Graph"` under
`nitrite`, `pH`, and `nitrate` DataPoints. Other metadata classes default to
`this::class.simpleName!!` (e.g. `FilterMetaData`, `TriggerMetaData`), which
gave them per-subtype names — Graph has no subtypes, so the literal default
was the only thing reaching the wire.

## Fix

1. `GraphMetaData.name` defaults to `""` instead of the colliding literal.
2. `Node.name()` falls back to `"Graph"` when the meta name is empty (matches
   the `Camera` / `Backup` `.ifEmpty { … }` pattern).
3. `KrillNodeTypes` Graph default meta has `name = ""` so the registry no
   longer hands out the colliding literal either.
4. `CreateNodeTool` derives `"<parent DataPoint name> graph"` from the
   verified parent when the caller did not supply a `name`. Parents always
   exist at create time (the tool already fetches them for validation), so
   this is free.

## Prevention

- Default values that collide across siblings are a smell — either they
  should be derived from local context (parent, position) or removed in
  favour of a non-defaulted parameter so the compiler points at every
  call site.
- When adding a new node-type metadata, prefer one of: `name = ""` with a
  display-time fallback (UI gets to decide), `name = this::class.simpleName!!`
  for types with multiple subtypes, or no default at all if the class needs a
  caller-supplied identity.
- The MCP `defaultMeta` table in `KrillNodeTypes.kt` mirrors the SDK
  defaults; keep them in sync or the MCP tool path will reintroduce the
  exact literal the SDK just removed.
