# 2026-07-14

> @ben — 5 PRs queued (13 days).

# Release candidate: 2026-07-01 → 2026-07-14

## Summary

This release includes a release candidate merge from agents → main, with SDK fixes to make `displayName()` abstract and expose node names for 14 metas (addressing #195), plus a CI retry tweak for the close-issues-on-merge workflow to handle race conditions with automerge. It follows a 13-day gap since the last release.

## Substantive changes


### #196 fix(krill-sdk): make displayName() abstract; surface node names on 14 metas (#195) (`risk:medium`)

## Summary

### #197 fix(krill-sdk): make displayName() abstract; surface node names on 14 metas (#195) (`risk:medium`)

## Summary

## Routine maintenance

- #191 Release notes release-2026-07-01 (`trivial`)
- #192 Release candidate: agents → main (`unlabeled`)
- #199 fix(ci): retry close-issues-on-merge when merged flag races automerge (#198) (`trivial`)

## Patterns Kraken noticed

- A recurring pattern is the systematic abstraction of `displayName()` in `krill-sdk` to improve node name visibility and reduce duplication across meta representations.  
- Multiple CI and release train improvements (e.g., `close-issues-on-merge` retries, timing gates, canary deployment) indicate an ongoing focus on stabilizing automated release workflows.  
- Consistent work on `NodeObserver` lifecycle (`scoped-base`, `autocloseable`) and node creation (parent name resolution, parent referencing) suggests active refactoring of node provisioning and state management.

## Open friction issues

_None open._

## Stats
- 5 PRs merged to `agents` since last release
- 0 risk:high, 2 risk:medium, 3 risk:low+trivial
- Days since last release: 13
- Lessons added: 15

---

# 2026-07-01

# Release candidate: 2026-06-27 → 2026-07-01

## Summary

This release train delivers release notes for the 2026-06-27 release candidate and corrects CI release-notes versioning parity from merge commits. It also introduces a new `AbstractNodeObserver` base class in the Krill SDK to support structured scope and lifecycle management for node observers, building on prior work around scoping, autocloseable behavior, and node-state invocation guards.

## Substantive changes


_None this batch._

## Routine maintenance

- #183 Release notes release-2026-06-27 (`trivial`)
- #185 Release candidate: agents → main (`unlabeled`)
- #184 fix(ci): release-notes version from merge commit (parity) (`trivial`)
- #187 fix(krill-sdk): add AbstractNodeObserver base class for structured scope management (#186) (`low`)

## Patterns Kraken noticed

- Consistent use of structured base classes for observer patterns (e.g., `AbstractNodeObserver`) to enforce scope and lifecycle management in node subsystems.  
- CI/release流程 tightening around versioning parity (merge-commit-aware release notes) and gate control (release-train CI/canary flow).  
- Recurring focus on node instantiation, metadata, and parent-child naming resolution across recent PRs and lessons.

## Open friction issues

_None open._

## Stats
- 4 PRs merged to `agents` since last release
- 0 risk:high, 0 risk:medium, 4 risk:low+trivial
- Days since last release: 4
- Lessons added: 15

---

# 2026-06-27

# Release candidate: 2026-06-27 → 2026-06-27

## Summary

This release candidate updates the CI configuration to use the correct PAT for the Kraken runner and points the Dev Agent Blue environment to the `agents` branch. It also includes a release-train canary test to validate the CI pipeline. No functional changes or bug fixes beyond CI adjustments.

## Substantive changes


_None this batch._

## Routine maintenance

- #179 test(ci): release-train canary (`trivial`)
- #180 Release candidate: agents → main (`unlabeled`)
- #181 fix(ci): source kraken runner PAT instead of a missing secret (`trivial`)
- #182 chore(ci): point Dev Agent Blue at the agents branch (`trivial`)

## Patterns Kraken noticed

- CI configuration continues to rely on fragile secret handling (e.g., PAT sourcing, branch references), indicating a need for robust, versioned CI parameterization.  
- Repeated fixes around node metadata, state, and interfaces (`NodeState`, `NodeMeta`, `NodeObserver`) suggest ongoing structural instability in the core agent data model.  
- The `release-train` and CI pipeline work (`#179`, `#181`, `#182`) points to a growing operational complexity in release automation that risks manual intervention without stricter guardrails.

## Open friction issues

_None open._

## Stats
- 4 PRs merged to `agents` since last release
- 0 risk:high, 0 risk:medium, 4 risk:low+trivial
- Days since last release: 0
- Lessons added: 15

---

# Release history

Narrative release notes, newest first. Each entry is the integration-PR
description Kraken maintained for that batch, appended automatically on merge
to `main`. See kraken `docs/agent-workflow.md`.

---
