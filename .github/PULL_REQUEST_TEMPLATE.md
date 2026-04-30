<!--
PR template for Sautner-Studio-LLC/krill-oss. The dev agent's CLAUDE.md mandates
this shape — keep all four sections, even if a section is one line.

`Closes #<n>` is mandatory. CI rejects PRs without a lesson entry under
`docs/lessons/YYYY-MM-DD-<slug>.md`.
-->

## Summary

<!-- 1–3 sentences. What changed and why, from the user's POV. Avoid restating the issue title. -->

## Approach

<!-- The how. Bullet the file-level changes; pick option-vs-option if you considered alternatives. Include any cross-repo escalation (filed Sautner-Studio-LLC/krill#<m>?). -->

## Introducing commit

<!-- `git log -S '<symbol>'` result that introduced the bug, plus a one-line summary. If genuinely cannot find one (e.g. predates this repo's history), say "predates split from Sautner-Studio-LLC/krill" and link the QA issue's evidence. -->

## Test plan

<!-- Checkboxes the reviewer can run. Include the gradle command, a single-test selector if relevant, and any out-of-CI verification (live swarm probe, Maven local install, etc.). -->

- [ ] `./gradlew ...`
- [ ] <regression test the QA agent should reproduce on a live swarm>

Closes #<issue>

<!-- Add `@qa-agent please verify` as a separate comment after the PR opens, not inline here — it's a request to act, not part of the PR description. -->
