# 2026-09-19

# Release candidate: 2026-09-17 → 2026-09-18

## Summary

This release primarily enhances `krill-pi4j` with servo/ESC control via angle or microseconds (not duty-cycle), adds a PCA9685 16-channel PWM driver client, SPI support, and fixes several integration and PWM rounding issues while ensuring mock mode works correctly. It also includes minor fixes in `krill-mcp` and CI, alongside release infrastructure updates.

## Substantive changes


### #253 fix(krill-pi4j): select real Pi4J providers instead of registry-only stubs (#244) (`risk:medium`)

## Summary

### #257 feat(krill-pi4j): add servo API taking angle or microseconds, never duty-% (#248) (`risk:high`)

## Summary

### #265 fix(krill-pi4j): ship pi4j-plugin-mock in the daemon so PI4J_MOCK=true actually works (`risk:medium`)

## Summary

## Routine maintenance

- #237 fix(krill-mcp): resolve create_node type selectors by unique leaf name (#236) (`low`)
- #251 Release notes release-2026-09-17 (`trivial`)
- #252 Release candidate: agents → main (`unlabeled`)
- #255 feat(krill-pi4j): add SetPulse(period_ns, duty_ns) — the servo/ESC path percent cannot express (#246) (`low`)
- #254 fix(krill-pi4j): round PWM duty cycle instead of truncating (#245) (`low`)
- #256 feat(krill-pi4j): add PCA9685 client — 16-channel PWM over I2C with OE as a first-class concept (`low`)
- #258 chore(krill-pi4j): consolidate version to a single gradle.properties source (#249) (`trivial`)
- #260 feat(krill-pi4j): add SpiService to the daemon and client (#250) (`low`)
- #261 fix(krill-pi4j): remove unused dialout/tty group grants from postinst (#259) (`low`)
- #263 fix(ci): expand DB-wipe glob under sudo so it actually reads a 0750 dir (#262) (`low`)

## Patterns Kraken noticed

- **Hardware abstraction consistency in `krill-pi4j`**: Repeated focus on replacing stubs with real providers (#253), adding proper PWM/duty rounding (#254), introducing dedicated servo/ESC APIs (angle/µs instead of %) (#257, #255), and explicit PCA9685/I2C support (#256) indicates a systematic move toward robust, production-grade hardware control APIs.  
- **Release engineering and infrastructure polish**: Consolidating versioning (#258), fixing CI/CD scripts (e.g., sudo glob expansion for DB wipe #263), and ensuring mock support ships in the daemon (#265) show recurring attention to reproducible builds and runtime fidelity.  
- **Type safety and disambiguation**: PR #236 (resolving type selectors by unique leaf name) suggests an ongoing effort to avoid ambiguous metadata resolution, likely tied to MCP tooling and node identification patterns.

## Open friction issues

_None open._

## Stats
- 13 PRs merged to `agents` since last release
- 1 risk:high, 2 risk:medium, 10 risk:low+trivial
- Days since last release: 1
- Lessons added: 15

---

# 2026-09-17

> ## @ben — 6 PRs queued, 23 days since last release.

# Release candidate: 2026-08-01 → 2026-08-24

## Summary

This release incorporates agent-facing fixes in `krill-mcp` and `krill-sdk`, addressing type resolution, null error messages, and UI label visibility, alongside documentation improvements and release tooling updates. It follows a 23-day interval and includes the final `agents → main` release candidate merge.

## Substantive changes


_None this batch._

## Routine maintenance

- #230 Release notes release-2026-08-01 (`trivial`)
- #231 Release candidate: agents → main (`unlabeled`)
- #237 fix(krill-mcp): resolve create_node type selectors by unique leaf name (#236) (`low`)
- #235 fix(krill-sdk): add name field to MqttMetaData so canvas shows label (`low`)
- #239 fix(krill-mcp): fall back to exception class name when tool error message is null (`low`)
- #243 docs(lessons): add lessons README schema + SEO front matter CI guard (#242) (`trivial`)

## Patterns Kraken noticed

- Consistent focus on tooling and SDK usability fixes: resolving null error messages, adding name fields for UI labeling, and refining type selector resolution.  
- Ongoing effort to stabilize release cadence: PR #231 merges the release candidate after 23 days, indicating a deliberate release-train approach with CI gates.  
- Documentation and CI hygiene improvements: enforced schema + SEO front matter for lessons via CI guard, aligning with prior lessons on release-train gates and issue-closure automation.

## Open friction issues

_None open._

## Stats
- 6 PRs merged to `agents` since last release
- 0 risk:high, 0 risk:medium, 6 risk:low+trivial
- Days since last release: 23
- Lessons added: 15

---

# 2026-08-01

# Release candidate: 2026-07-31 → 2026-08-01

## Summary

This release adds Swarm dispatch MCP tools to `krill-mcp`, merges the `agents` branch (likely including recent agent-related work), and applies a minor patch update. It follows a 1-day gap since the prior release, indicating a small, focused increment.

## Substantive changes


### #224 feat(krill-mcp): add swarm dispatch MCP tools (`risk:medium`)

## Summary

## Routine maintenance

- #228 Release candidate: agents → main (`unlabeled`)
- #229 Bsautner patch 4 (`unlabeled`)

## Patterns Kraken noticed

- PR #224 introduces MCP tools for swarm dispatching, continuing a pattern of integrating external LLM orchestration (consistent with prior PRs like #216 and recent lessons on swarm/LLM contracts).  
- PR #228 promotes a release candidate directly from `agents` to `main`, reflecting an established "release train" workflow (per the 2026-06-27 release-train-runner CI gates and canary patterns).  
- PR #229 is a minor documentation/typo fix, indicating ongoing small-but-essential polish work with no recurring technical theme.

## Open friction issues

_None open._

## Stats
- 3 PRs merged to `agents` since last release
- 0 risk:high, 1 risk:medium, 2 risk:low+trivial
- Days since last release: 1
- Lessons added: 15

---

# 2026-07-30

# Release candidate: 2026-07-14 → 2026-07-21

## Summary

This release merges the Krill agents branch to main, including fixes to the `krill-sdk` for `LLMMetaData` field completeness, `AbstractNodeObserver` lifecycle refinement, and a new `allowNetwork` opt-in in `LambdaMetaData`. It follows a 7-day gap since the prior release and incorporates recent release-train CI and issue-closure improvements.

## Substantive changes


_None this batch._

## Routine maintenance

- #202 Release notes release-2026-07-14 (`trivial`)
- #203 Release candidate: agents → main (`unlabeled`)
- #206 fix(krill-sdk): add numCtx/temperature/keepAlive fields to LLMMetaData (#205) (`low`)
- #211 fix(krill-sdk): make AbstractNodeObserver.close() final, add onClose() hook (#207) (`low`)
- #214 feat(krill-sdk): add allowNetwork opt-in to LambdaMetaData (#213) (`low`)

## Patterns Kraken noticed

- `AbstractNodeObserver` lifecycle management improved with a final `close()` method and `onClose()` hook, reinforcing an AutoCloseable pattern already evident in recent lessons (#207 → #211).  
- SDK metadata classes (`LLMMetaData`, `LambdaMetaData`) are being iteratively enhanced with runtime-configurable fields (`numCtx`, `temperature`, `keepAlive`, `allowNetwork`), indicating active model deployment customization work.  
- Release process automation remains active (7-day release cadence, release-candidate PRs), with ongoing CI gating and canary deployment patterns consistent with prior release-train infra.

## Open friction issues

_None open._

## Stats
- 5 PRs merged to `agents` since last release
- 0 risk:high, 0 risk:medium, 5 risk:low+trivial
- Days since last release: 7
- Lessons added: 15

---

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
