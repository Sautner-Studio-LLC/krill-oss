# Skill rule contradicted what the Krill server accepts

**Issue:** [krill-oss#12](https://github.com/Sautner-Studio-LLC/krill-oss/issues/12)
**Root cause category:** Documentation drift — absolute rule that doesn't
reflect runtime behaviour
**Module:** `krill-mcp` companion skill (`skill/krill/SKILL.md`)

## What happened

The "build this tree on my server" section of `SKILL.md` declared a hard
rule:

> `KrillApp.DataPoint` → parent is `KrillApp.Server` (or
> `KrillApp.Server.SerialDevice` for a sensor wired to a serial device).
> **Not a Project.** Projects own Diagrams, TaskLists, Journals, Cameras
> — not DataPoints.

But on a real swarm (`pi-krill-05.local`, server id
`b87160aa-9cb1-4cf2-ae81-0864a3619874`) DataPoints are routinely parented
by a `KrillApp.Project` (`Water Quality` → `ammonia` / `nitrite` /
`nitrate` / `pH`) and by other DataPoints (`ammonia` → `ammonia ppm`).
The server's polymorphic registry accepts both arrangements; only the
catalog `validParentTypes` lists the typical ones. `mcp-tools.md` already
acknowledged this:

> "If the parent's type isn't in the `validParentTypes` list for the
> requested type, the tool returns a `warnings[]` entry but still posts —
> the server will accept the node and the catalog may simply be behind."

…but `SKILL.md` did not, so an agent following it verbatim would refuse
to mirror an existing project-organised tree.

## Fix

Replaced the absolute rule with a "mirror the existing tree" instruction
that uses the catalog as a fallback, and explicitly notes that the
server is permissive about parent types. Added a JVM regression test
(`SkillRulesTest`) that fails if the absolute language is reintroduced
or if the new "permissive" caveat goes missing.

## Prevention

- Treat `validParentTypes` in the catalog as **typical placement**, not
  enforcement. The server's polymorphic registry decides what's
  accepted; the catalog is a hint.
- When a skill rule says "X must be Y", verify against a real swarm
  before promoting it to absolute language. If the runtime accepts
  alternatives, the rule is a preference, not a hard constraint.
- Cross-check the bundled JSON specs in
  `skill/krill/references/node-types/` with observed swarms when
  authoring new skill rules — the JSONs are upstream copies and may
  encode preferences the server doesn't enforce.
