# Release-train canary: first PR through the agents machine

**Root cause category:** CI/CD validation — exercising a new pipeline end-to-end
**Module:** repo plumbing (CI)

## What happened

First trivial PR opened against the long-lived `agents` branch to validate the
release-train machinery end-to-end: the `pr-risk-classify` second-opinion, the
`required-checks-passed` + `lessons-check` gate (a docs-only change skips the
path-filtered builds, so the aggregator must pass on skips), automerge-on-green,
and the `release-pr-update` regeneration of the integration PR body.

## Fix

Not a fix — a deliberate no-op canary. Confirms the gate tolerates skipped
builds, the risk classifier runs without blocking, and a `risk:trivial` PR
automerges to `agents` once green.

## Prevention

- Always shake out a new merge-automation pipeline with a trivial, reversible
  canary before cutting real agent traffic over to it.
- A docs-only canary is the cleanest probe of the "skipped builds must not
  deadlock the aggregator" invariant.
