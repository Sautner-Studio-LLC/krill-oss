# End-to-end agent verification: Dev Agent Blue + Verify Agent Ghost

**Issue:** [Sautner-Studio-LLC/krill-oss#58](https://github.com/Sautner-Studio-LLC/krill-oss/issues/58)
**Root cause category:** Repo plumbing — agent workflow smoke test
**Module:** repo plumbing (`.github/workflows/Dev Agent Blue.yml`,
`.github/workflows/Verify Agent Ghost.yml`)

## What happened

Issue #58 was filed as a deliberate smoke test of the new agent
workflows landed in 9aecfef ("ci: add Dev Agent Blue + Verify Agent
Ghost workflows"). The intent: confirm that an issue assigned to
`krill-blue-bot` on this repo triggers the Dev Agent Blue runner,
that the runner opens a PR back into this repo (not the sibling),
and that a `needs-qa-verify` label hands the PR off to Verify Agent
Ghost (`krill-ghost-bot`) for verification — without any human in
the loop.

The smoke test is intentionally trivial: change a markdown file,
open a PR, label it. The interesting signal is the routing, not
the diff.

### Friction noted in the issue body

The issue text says *"label the pr with needs-qa-testing"* and
*"if you encounter a label called need-qa-testing reference add to
your pr exactly where you saw that"*. No such label exists on this
repo — `gh label list --search qa` returns `qa`, `needs-qa-verify`,
and `dev-task`. The dev agent's CLAUDE.md (and `shared/workflow.md`)
canonically names the handoff label **`needs-qa-verify`**, which is
what this PR applies. Recording the discrepancy here so future
filings of test issues can use the canonical name and the bot
doesn't have to choose between following the issue body literally
or following CLAUDE.md.

## Fix

1. `SECURITY.md` — appended a one-line HTML comment marking the
   file as the touchpoint of the e2e verification, with a back-link
   to this lesson. Trivial diff; the value is in the workflow it
   exercises, not the content.
2. `docs/lessons/2026-05-04-e2e-agent-verify.md` — this entry,
   recording the smoke-test outcome and the `needs-qa-testing` /
   `needs-qa-verify` naming friction so the next agent doesn't
   re-derive it.

## Prevention

- When a test issue body names a label, the filer should `gh label
  list` first and use the actual name. Otherwise the agent has to
  decide between literal-following and CLAUDE.md-following on every
  smoke test, and the answer is recorded only in PR comments.
- Smoke-test issues should still ship the full PR shape (lesson
  entry, `Closes #<n>`, `needs-qa-verify`) — that's the artefact
  the workflows are actually verifying. Skipping the lesson "because
  it's just a test" would mean the smoke test stops exercising the
  CI path that rejects lessonless PRs, which is the whole point.
- If a future test issue intentionally wants to stress-test the
  agent's response to an unknown label, say so explicitly in the
  body. Otherwise treat label names in issue bodies as suggestions
  and verify against `gh label list`.
