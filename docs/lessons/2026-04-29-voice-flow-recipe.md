# Skill missing a natural-language → action workflow recipe

**Issue:** [krill-oss#27](https://github.com/bsautner/krill-oss/issues/27)
**Root cause category:** Skill-gap — primitives existed, recipe did not
**Module:** `krill-mcp` companion skill (`skill/krill/SKILL.md`)

## What happened

A QA agent on a Claude Desktop voice flow tried to act on *"turn on
the Vivarium mister"*. The skill covered discovery, multi-node
authoring, diagram authoring, and time-series writes, but had **no
recipe for the natural-language → action chain end-to-end** — the
flow that has its own choreography because voice has no inline diff
and a ~1-turn confirmation budget.

The agent reasoned every step from primitives:

1. Picked the only registered server (`pi-krill-05`) instead of
   resolving the phrase against the swarm.
2. Found a `LogicGate` (BUFFER) inside the local Vivarium project and
   chose it as the target — wrong: the actual mister-toggling NOT
   gate lives on `pi-krill.local`, which the registry didn't know
   about (`#23`).
3. Could not invoke a manual fire because no `execute_node` exists
   (`#24`); fell back to considering `record_snapshot`, which felt
   wrong for a voice flow because it pollutes the upstream
   DataPoint's history.
4. Gave up and asked QA for triage instead of firing.

A user expecting *"turn on the mister"* to just work would hear
silence (or a multi-turn exploration that defeats the point of voice).
The primitives — `find_node` (`#26`, just shipped), `record_snapshot`,
the catalog's `llmSideEffectLevel`, the parent/source/target wiring on
`LogicGate` — were all there. Without a recipe stringing them together
with the safety pattern (confirm against the *target's* side-effect
level, not the executor's), every voice flow had to invent it.

## Fix

Added a top-level workflow section to `SKILL.md`:
**"For 'natural-language commands → action' (voice or chat-mode
requests)"**. Five numbered steps:

1. **Resolve the target** via a single `find_node` call; ask the user
   only when two or more candidates score within ~0.1 of each other.
2. **Resolve the action** by mapping verbs ("turn on", "toggle") to a
   `LogicGate` whose `meta.target` is the named Pin/DataPoint.
3. **Confirm against the target's `llmSideEffectLevel`, not the
   executor's** — a LogicGate is `medium`, but the Pin it writes to
   is `high`, which is the real-world side effect.
4. **Fire** via `execute_node` when available (tracked as `#24`,
   not yet shipped); fall back to `record_snapshot` on the gate's
   source DataPoint *with a once-per-session warning* that this
   writes a synthetic reading into the upstream history.
5. **Report** by re-reading the target after a 1–2 second delay.

Added a "Failure modes to avoid" list calling out: silent
disambiguation; firing high-side-effect actions without
confirmation; using `record_snapshot` as a silent `execute`
substitute; multi-turn breadth-first exploration as a substitute
for one `find_node` call.

Added regression tests in `SkillRulesTest`:
- `skill has a natural-language commands workflow recipe`
- `voice-flow recipe spells out the resolve-confirm-fire chain`
  (asserts `find_node`, target-side-effect anchoring, `execute_node`
  preference)
- `voice-flow recipe warns against the snapshot-as-execute footgun`
  (asserts the synthetic-reading warning is present)

## Prevention

- **When primitives exist but agents still flounder, the missing
  piece is usually a recipe.** A skill is not just a tools index;
  it's the choreography that strings tools together for specific
  user intents. Discovery + record + diagram recipes existed;
  natural-language action did not, and that gap dominated the voice
  experience even after the underlying tools were fine.
- **Anchor safety on the *target* of the action, not the
  intermediary.** A LogicGate's `llmSideEffectLevel` is `medium`,
  but the Pin it writes to (the real-world thing the user perceives)
  is `high`. An agent checking only the gate's level would fire a
  mister/lamp/valve without confirmation. Recipes that involve
  executors must explicitly point the safety check at what the
  executor *does*, not what the executor *is*.
- **Voice flows have a hard ~1-turn confirmation budget.** Any
  recipe for them needs to converge in 1–2 round-trips on the happy
  path and fall back to exactly one disambiguating question on the
  edge cases. Patterns that involve exploration ("scan every
  server, filter, ask the user which one") fail in voice because
  the user perceives them as silence. Always pair an
  exploration-flavoured suggestion with a single composite primitive
  that collapses the round-trips (`find_node` here; whatever the
  next equivalent is for future flows).
- **Document fallbacks with their costs visible.** The
  `record_snapshot`-on-the-gate's-source workaround works, but
  silently leaves a synthetic reading in the upstream DataPoint's
  history. The recipe needs to make that visible to the user once
  per session so they can decide whether to wait for `execute_node`
  or accept the trade-off — agents that pretend the fallback is a
  drop-in replacement teach users to distrust the swarm's data.
- **Skill recipes need regression tests.** A markdown assertion that
  the recipe still mentions `find_node` / `execute_node` /
  `synthetic` is enough to catch a future "cleanup" pass that
  rewrites the section in a way that drops the safety pattern. The
  test is cheap; the cost of silently dropping a recipe is
  invisible until the next voice user.
