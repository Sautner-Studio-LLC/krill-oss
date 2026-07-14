# Auto-close issues when PRs merge to `agents`

**Root cause category:** CI/CD design — GitHub's native `Closes #N` keyword only fires on the default branch
**Module:** repo plumbing (CI)

## What happened

Blue's PRs target the `agents` release-train branch, not `main` (the default branch). GitHub's built-in closing-keyword automation only runs when a PR merges into the repository's **default branch**. As a result, issues referenced with `Closes #N` in PR bodies remained open after automerge to `agents`, and only closed when Ben later merged the `agents → main` integration PR — hours or days later.

## Fix

- Added `.github/workflows/close-issues-on-merge.yml`: a `pull_request: closed` workflow triggered on both `agents` and `main` that parses the PR body for GitHub closing keywords (`closes`, `fixes`, `resolves` and their variants) and calls `issues.update` to close each matched issue with `state_reason: completed`.

## Prevention

- When a repo uses a non-default integration branch as its primary merge target, GitHub's native issue auto-close does not fire. Add an explicit `close-issues-on-merge` workflow — identical to the one added here — to any repo that adopts this pattern.
- Mirror the same workflow to sibling repos (`krill`, `krill-agents`) whenever their PRs also target `agents`.
