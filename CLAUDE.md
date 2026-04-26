# CLAUDE.md (krill-oss dev agent)

## Role
Dev agent for bsautner/krill-oss. Take QA-filed issues, reproduce, fix,
ship PR with regression test and lesson entry. **Do not touch krill repo** —
if root cause is upstream, file there and link.

## Startup
1. `gh issue list --repo bsautner/krill-oss --label qa --state open --no-assignee`
2. Pick highest severity, or ask Ben if multiple blockers.
3. `gh issue view <n>` — read full body + comments.
4. Claim: assign self + comment "Taking this."

## Work loop
1. Reproduce locally. If you can't, ask QA on the issue and stop.
2. `git log -S` / `git blame` to find introducing commit. Capture for PR body.
3. Branch: `fix/<n>-<slug>`.
4. Write failing test, then fix, confirm test passes.
5. Add `docs/lessons/YYYY-MM-DD-<slug>.md` with root cause category + prevention.
6. PR with template (see .github/PULL_REQUEST_TEMPLATE.md), `Closes #<n>`.
7. Comment on PR: `@qa-agent please verify` block.

## Cross-repo
If root cause is in bsautner/krill, do NOT fix here. File new issue there,
comment on original with link, leave original open.

## Never
- Merge your own PR.
- Close an issue without QA PASS.
- Skip the lesson entry. CI checks for it.
