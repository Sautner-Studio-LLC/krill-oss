# `close-issues-on-merge` races GitHub's async automerge

**Root cause category:** CI/CD design — webhook payload snapshot is stale relative to async state transition
**Module:** repo plumbing (CI)

## What happened

`close-issues-on-merge.yml` (added in `a1ddae4` / `#190`) gated its job on `if: github.event.pull_request.merged == true`. `gh pr merge --auto --squash` queues the actual squash-merge asynchronously once required checks pass, so GitHub can deliver the PR's `closed` webhook — and fire this workflow — before `pull_request.merged` flips to `true` in the payload. Observed directly on `krill-oss#197`'s merge: the `closed` event fired at `2026-07-14T00:17:24Z`, but the PR's actual `mergedAt` was `2026-07-14T00:18:32Z`, 68 seconds later. The job-level `if:` evaluated false against the stale snapshot, the job was skipped, and `krill-oss#195` stayed open despite its closing PR having merged.

## Fix

- `.github/workflows/close-issues-on-merge.yml`: dropped the job-level `if: github.event.pull_request.merged == true` gate.
- Moved the merge check inside the `actions/github-script` step: if the payload's `pull_request.merged` is false, re-fetch the PR via `github.rest.pulls.get` up to 6 times, 15s apart (comfortably covers the observed 68s lag), before concluding "closed without merging" and returning early.

## Prevention

- Don't gate a `pull_request: types: [closed]` workflow on `merged` straight from the webhook payload when merges can be queued asynchronously (`gh pr merge --auto`, branch protection auto-merge, merge queues). The payload is a snapshot at delivery time, not a guarantee of final state — re-fetch the resource inside the job before trusting a boolean that describes an in-flight transition.
- This is the third copy of this workflow (`krill`, `krill-oss`, `krill-agents`) to need the same fix — `krill-agents#55` → `krill-agents#56` shipped it first; this PR ports it here. Check sibling repos for the same latent bug before assuming a fix is repo-specific.
