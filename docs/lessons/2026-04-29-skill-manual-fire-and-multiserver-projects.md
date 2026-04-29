# Skill missed two scenarios surfaced by a multi-server QA session

**Issue:** [krill-oss#25](https://github.com/bsautner/krill-oss/issues/25)
**Root cause category:** Documentation gap — workflows that were
discoverable but undocumented, so future agents would rediscover them
inconsistently
**Module:** `krill-mcp` companion skill (`skill/krill/SKILL.md`)

## What happened

A QA session asked the skill-driven agent to (1) toggle a
`KrillApp.Server.Pin` named "Vivarium Mister" on `pi-krill.local` by
firing its NOT-gate child, and (2) operate against a swarm where both
`pi-krill.local` and `pi-krill-05.local` happened to host a project
named `Vivarium`. Two real gaps surfaced:

**(A) No recipe for manually firing a Trigger / Executor / LogicGate.**
`SKILL.md` has a "record values to a DataPoint" workflow but never
connects "fire this gate once" — the in-app manual-execute equivalent —
to "synthesize a snapshot on its source DataPoint." The agent reasoned
its way to the `record_snapshot`-upstream pattern by reading the node
catalog directly, but a future agent would rediscover this
inconsistently and wouldn't know it's a stopgap that *pollutes the
upstream DataPoint's history*.

**(B) No guidance for disambiguating same-named projects across peers.**
The "Pick the parent project" step in the SVG-dashboard workflow
assumed single-server semantics: "if exactly one project exists, use
it; if several, ask the user which one." In a multi-server registry
that falls through silently when two servers each have a project named
`Vivarium`, because `list_projects` returns matches from each server
and the skill never names `(serverId, projectId)` as the identity.

## Fix

Two targeted additions to `SKILL.md`:

- A new top-level workflow, **"For 'fire a Trigger or Executor
  manually'"**, sitting alongside "For 'record values to a
  DataPoint'." It points at the canonical `execute_node` recipe
  (tracked upstream as #24) and documents the `record_snapshot`-upstream
  stopgap with explicit caveats — pollutes history, doesn't generalize
  to gates with no DataPoint source — so an agent following the skill
  doesn't promote the workaround to canonical guidance.
- A bullet in **"Topology, auth, limits"** stating that project (and
  node) names are not swarm-unique, that `(serverId, projectId)` is the
  identity, and that the agent must ask the user which server when
  `list_projects` returns same-named matches across peers. The
  SVG-dashboard "Pick the parent project" step now cross-references
  that bullet so the multi-server case is handled at the point of use.

JVM regression tests (`SkillRulesTest`) guard each addition: they fail
if the "fire a Trigger or Executor manually" heading goes missing, the
`record_snapshot`-upstream stopgap stops being framed as a stopgap, the
"project names are not swarm-unique" sentence disappears, the
`(serverId, projectId)` identity language is dropped, or the
"which server" disambiguation prompt is removed.

## Prevention

- When QA hits a workflow gap, prefer **adding a top-level workflow
  section in `SKILL.md`** over burying the recipe in
  `references/mcp-tools.md`. The references file is read on demand; an
  agent dispatching on the user's intent ("fire this gate") needs the
  pointer in the auto-loaded surface or it won't find it.
- For workarounds that the runtime allows but the docs don't endorse
  (here: `record_snapshot`-upstream as a poor man's `execute_node`),
  document them with an explicit **"this is a stopgap"** frame plus the
  caveats. Silence invites a future agent to promote the workaround to
  canonical guidance and pollute real data.
- When a section's logic ("if N exist, ask which one") assumes a
  scope (single server, single project, single peer), say so
  explicitly. Multi-server semantics are now the common case once
  transitive peer discovery (#23) ships, and any guidance that
  silently dispatches on count alone needs `(serverId, *)` as a
  qualifier.
- Every skill rule that mentions a name-keyed lookup should call out
  whether the name is unique in the relevant scope. For Krill,
  `(serverId, nodeId)` is the only true identity — names are display
  labels.
