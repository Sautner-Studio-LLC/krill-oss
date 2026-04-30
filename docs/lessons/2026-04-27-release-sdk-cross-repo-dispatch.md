# krill-sdk releases needed manual cross-repo dispatch

**Issue:** [krill-oss#17](https://github.com/Sautner-Studio-LLC/krill-oss/issues/17)
**Root cause category:** CI/CD friction — split source-of-truth and
publish pipeline across two repos with no automatic handoff
**Module:** `krill-sdk` (build), repo plumbing (CI)

## What happened

`krill-sdk` source lives in this repo, but the Maven Central publish
workflow lives in the **private** `Sautner-Studio-LLC/krill` repo (because the
Sonatype / GPG / AWS / CloudFront secrets live there). A merge to
`krill-oss/main` that touched `krill-sdk/` produced no published
artifact until someone manually opened `Sautner-Studio-LLC/krill`'s Actions tab,
clicked **Run workflow** on `Publish Krill-SDK Maven.yml`, and typed
the new version. QA #156 / krill-oss #14 sat for ~5 hours after merge
with no consumer-visible artifact, surfacing as krill-oss #17 from the
upstream dev agent.

Two contributing factors:

- The dev agent's CLAUDE.md explicitly told it **not** to bump module
  versions in bug-fix PRs. So even when a fix landed, the
  `build.gradle.kts` `version` line on `main` matched the
  already-published Maven Central version, and the publish workflow's
  default would just re-publish the same coordinates (which Maven
  Central rejects).
- The publish workflow is the obvious place to bump and dispatch
  automatically, but it lives in a repo this dev agent doesn't write to.

## Fix

- **Per-PR bump rule.** Every PR touching `krill-sdk/**` now bumps the
  patch in `krill-sdk/build.gradle.kts` in the same change. CLAUDE.md's
  *Conventions → Versioning* section was rewritten to document this
  (and to keep the *don't bump* rule for `krill-mcp` / `krill-pi4j`,
  which release deliberately, not per-PR).
- **Auto-dispatch workflow.** `.github/workflows/release-sdk.yml`
  triggers on push to `main` when `krill-sdk/build.gradle.kts` changes,
  detects whether the version line actually moved (vs. unrelated edits
  to that file), and calls
  `gh workflow run "Publish Krill-SDK Maven.yml" --repo Sautner-Studio-LLC/krill`
  with the new version. Manual `workflow_dispatch` trigger is preserved
  for republishes after a half-failed upstream run.
- **Token plumbing.** Dispatch uses a `KRILL_PUBLISH_TOKEN` repo secret
  (fine-grained PAT, scoped to `Sautner-Studio-LLC/krill` with `Actions:
  read+write`, `Contents: read`). The workflow checks for the token
  and surfaces an actionable error if it's missing — rather than
  silently 401-ing.

## Prevention

- When a "release-on-demand" pipeline lives in a different repo from
  its source, treat the **dispatch** as a first-class CI concern, not
  a wiki step. Manual-dispatch friction grows linearly with release
  cadence; automation is one workflow file away.
- Source-of-truth versions should live next to the code they describe
  (`build.gradle.kts` here), not in the publish workflow's input. The
  publish workflow should *honour* the in-repo version, not invent it.
- A "don't bump versions in bug-fix PRs" rule is correct for downstream
  modules with deliberate release cadences (e.g. Debian-packaged
  daemons). It's wrong for upstream library modules whose downstream
  consumers can't pick up a fix until a new artifact lands. Tag the
  rule per module, not per repo.
- The `concurrency: cancel-in-progress: false` setting on release
  workflows matters: cancelling a publish mid-upload can leave a
  partial release on Maven Central that's painful to clean up.
