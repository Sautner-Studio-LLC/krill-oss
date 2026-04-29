# krill-mcp registry forced shell+restart to address an unconfigured peer

**Issue:** [krill-oss#23](https://github.com/bsautner/krill-oss/issues/23)
**Upstream relay (deferred Part A):** [bsautner/krill#178](https://github.com/bsautner/krill/issues/178)
**Root cause category:** API design — the MCP layer treated each Krill
server as an opaque endpoint, even though the underlying platform is a
swarm where every node carries `(nodeId, hostId)` and every server
already knows its peers.
**Module:** `krill-mcp`

## What happened

A Claude Desktop session had a single MCP-configured seed
(`pi-krill-05.local`) and was asked to fire a `KrillApp.Executor.LogicGate`
on a *peer* host (`pi-krill.local`). The agent knew the peer hostname —
the peer was a real, reachable Krill server in the same swarm with the
same PIN-derived bearer — but could not address it through the MCP. The
tool call errored with:

> `No Krill server matches 'pi-krill.local' (and no default is registered).`

The wording wrongly implied the host didn't exist. The real problem was
that `KrillRegistry` was bootstrap-only: it built `KrillClient`s once
from `config.seeds[]` and never grew. The only way to reach a peer was
to edit `/etc/krill-mcp/config.json`, restart the daemon — which the
agent didn't have shell access to do — and even with shell, the restart
interrupted other in-flight work.

The original `KrillRegistry` doc-comment said "Peer auto-discovery from
ServerMetaData is deliberately out of scope for v1." That decision was
defensible when the only consumers were inside the same machine; it
didn't survive multi-Pi deployments.

## Fix

Two complementary changes were possible: (A) transitive peer discovery
from one seed, and (B) lazy host registration on first reference. They
are independent and we shipped them separately.

**Part B (this PR):** lazy host registration. `KrillRegistry.resolve()`
now falls through to `tryRegisterByHost(selector)` when the registry
has no match and the selector looks like a host. The new path:

- `krill-mcp-service/src/main/kotlin/krill/zone/mcp/krill/KrillRegistry.kt`
  — added `tryRegisterByHost()` and a `companion.looksLikeHost()`
  predicate. The predicate requires a literal `.` or `:` so UUID-shaped
  selectors (`f47ac10b-58cc-4372-a567-...`) don't trigger a 5s connect
  timeout for nothing.
- `krill-mcp-service/src/main/kotlin/krill/zone/mcp/mcp/tools/KrillTools.kt`
  and `.../tools/DiagramTools.kt` — both `resolve()` helpers now emit
  `host unreachable: <selector>` when the lazy dial fails (vs. the old
  `"no match"` wording, which was indistinguishable from "host doesn't
  exist").
- `skill/krill/SKILL.md` and `skill/krill/references/mcp-tools.md` — the
  "Topology, auth, limits" bullet and the "Adding a new Krill server at
  runtime" troubleshooting block were rewritten to describe the new
  resolution order.

Tests:
`krill-mcp-service/src/test/kotlin/krill/zone/mcp/krill/KrillRegistryLazyRegistrationTest.kt`
— exercises the predicate (FQDN/IP/host:port → true; bare names, UUIDs,
blanks → false) and verifies that an unreachable `.invalid` host returns
null without polluting the registry.

**Part A (deferred):** transitive autodiscovery — "given one seed, find
every peer in the swarm without the agent typing each hostname" — is
gated on the krill server actually exposing a peer list. `/health`
currently filters out `KrillApp.Server` nodes (`server/.../Routes.kt`
around the `/health` route), so the MCP has nothing to recurse on.
Tracked upstream as bsautner/krill#178; once that lands, `bootstrap()`
and `reseed()` can walk the swarm from a single seed and lazy
registration becomes a fallback rather than the primary path.

## Prevention

- **When the underlying platform is multi-instance, the proxy layer
  should be too.** The MCP layer was modeled as "one daemon, one
  server" because the simplest deployment is co-located with a Krill
  server. The instant the first multi-Pi swarm came online, that model
  became wrong — but the registry kept the single-server assumption
  encoded in `seeds[]` boot only. When wrapping a clustered system,
  the wrapper should grow with the cluster, not freeze at startup.
- **Error messages must distinguish "I don't know this" from "I tried
  and failed."** The original `"No Krill server matches '<x>'"`
  conflated two very different failure modes. An agent that gets
  *"unreachable"* knows to check DNS/port/bearer; one that gets *"no
  match"* assumes the host is fictional and asks the user to restate.
  Wording is part of the API contract.
- **A lazy fallback path is not the same as autodiscovery.** Lazy
  registration covers "the agent already knows the hostname"; transitive
  discovery covers "the agent shouldn't need to." Both are useful; one
  isn't a substitute for the other. Document the gap when only one is
  shipped, so the agent's expectations stay calibrated.
- **Heuristic predicates against the UUID space need a guard.** A naive
  `looksLikeHost(s)` of `s matches /^[a-z0-9-]+$/` would have matched
  every unknown UUID and fired a pointless DNS lookup. Requiring `.`
  or `:` in the selector excludes UUIDs cleanly without losing real
  hostnames (which carry `.local` in this swarm anyway). When a
  predicate's accept set overlaps with another well-defined identifier
  space, narrow it.
