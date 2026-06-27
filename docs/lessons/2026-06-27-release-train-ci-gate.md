# Release-train CI gate: aggregate, don't require conditional jobs

**Root cause category:** CI/CD design — branch protection on conditional, path-filtered build jobs
**Module:** repo plumbing (CI), `build.yml`

## What happened

The Krill agent fleet is moving to a release-train model: agents merge to a
long-lived `agents` branch with automerge-on-green, and Ben merges a permanent
`agents` → `main` integration PR to ship (see kraken `docs/agent-workflow.md`).
Automerge needs a meaningful "green" to gate on, but `krill-oss`'s `build.yml`
runs three **path-filtered** jobs (`krill-sdk` / `krill-mcp` / `krill-pi4j`)
that are *skipped* on a PR that doesn't touch their tree. Marking those jobs
directly as required status checks would deadlock any PR that legitimately
skips one (GitHub treats a never-reported required check as pending forever).
There was also no CI enforcement of the long-standing "every change needs a
`docs/lessons/` entry" rule.

## Fix

- Added a **`required-checks-passed`** aggregator job (`always()`, depends on
  the build jobs + `lessons-check`) that treats **skipped** as acceptable but
  **failure/cancelled** as fatal. This single job is the only required context.
- Added a **`lessons-check`** job that passes if the PR adds/modifies a
  `docs/lessons/*.md` file or the PR body contains `no-lesson-needed`.
- Widened the `push` trigger to `[main, agents]` so pushes to `agents` build.
- Branch protection (`kraken/scripts/github/branch-protection.json`) requires
  exactly `required-checks-passed` + `lessons-check` on `agents`.

## Prevention

- **Never mark a conditional/path-filtered job as a required status check.**
  Gate on an `always()` aggregator that tolerates `skipped` and fails only on
  `failure`/`cancelled`. The aggregator is the contract; the individual jobs
  are implementation detail.
- Keep required-check **context names** (the job `name:`) in lockstep with the
  `contexts` array in `branch-protection.json` — a typo silently never-gates.
