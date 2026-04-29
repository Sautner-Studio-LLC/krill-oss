# `find_node` — single-call name resolution across the swarm

**Issue:** [krill-oss#26](https://github.com/bsautner/krill-oss/issues/26)
**Root cause category:** Missing capability — composite of low-level primitives
**Module:** `krill-mcp`

## What happened

A voice flow ("turn on the Vivarium mister") had no MCP tool that mapped a
free-text phrase to a concrete `(serverId, nodeId)`. The agent had to drive
the resolution by hand: `list_servers` → `list_nodes` per server → filter by
type → substring-match `displayName` → `get_node` to confirm — then
disambiguate when multiple candidates matched. Four to six round-trips per
voice command, with a wrong-target failure mode the agent couldn't detect
when the right server wasn't in the registry.

The underlying primitives were fine on their own. They just didn't compose
into a single resolution call: `Node` has no top-level `name`/`tags` field
(names live in per-type `meta`), `list_nodes` lifts `meta.name` to
`displayName` but doesn't search across servers, and there was no scoring
layer above the type-and-name primitives.

## Fix

1. New `FindNodeTool` (`krill-mcp/.../mcp/tools/FindNodeTool.kt`). Iterates
   every registered `KrillClient` (default `scope: "swarm"`; `"server"`
   restricts to one), scores each node against the lowercased,
   whitespace-tokenised query, and returns the top-`limit` ranked candidates.
2. Scoring weights name match (3.0/token) over parent-path match (1.5/token)
   over type match (0.5/token), with a +2.0 bonus when the full joined query
   is a substring of the candidate's name. Scores are normalised into
   `[0, 1]` against the per-token name-match max so a perfect full match
   surfaces as `~1.0` and the agent can use `>= 0.9 with clear gap` as a
   "fire confidently" threshold.
3. Pure ranking is exposed as `internal fun rank(...)` so unit tests
   (`FindNodeToolTest`) drive it with synthetic node JSON — no live Krill
   server required.
4. Registered in `Main.kt`'s tool list and documented in
   `skill/krill/SKILL.md` and `skill/krill/references/mcp-tools.md`.

## Prevention

- When an agent workflow looks like "scan, filter, disambiguate, repeat per
  server", that's a composite-primitive smell. Add a server-side
  scan+score+rank tool rather than telling the agent to do it client-side
  every call — the cost of one MCP tool is negligible compared with the
  per-query token + latency tax.
- For tools that touch the registry, default to **all** registered servers
  and offer single-server as an opt-in. The opposite default (single-server,
  swarm as opt-in) silently truncates the search domain and gives the agent
  a wrong-target failure mode it can't even detect — which is exactly the
  Vivarium-on-pi-krill case in #26.
- Bias scoring toward recall and let the caller decide. Returning `[]` for
  ambiguous queries pushes the disambiguation cost back onto the caller;
  returning ranked candidates lets the agent ask the user when scores are
  close and fire when they aren't.
- Pure ranking helpers are testable; tools that bake scoring into a single
  `execute()` are not. Factor scoring out of any future search/match tool
  so a unit test can drive it without a live Krill server.
