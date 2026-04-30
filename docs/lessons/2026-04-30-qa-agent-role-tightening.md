# QA agent's CLAUDE.md was permissive enough to invite drift

**Issue:** [Sautner-Studio-LLC/krill-oss#36](https://github.com/Sautner-Studio-LLC/krill-oss/issues/36)
**Root cause category:** Documentation ambiguity — agent-facing
prompt that reads as advisory rather than absolute
**Module:** repo plumbing (`krill-qa/CLAUDE.md`)

## What happened

`krill-qa/CLAUDE.md` told the QA agent: *"Do not make code changes
in either repo — observe, file, and stop."* On 2026-04-30 the QA bot
edited its own CLAUDE.md twice — first to fix stale `bsautner/*` paths
after the org move, then to add an always-assign-to-`krill-blue-bot`
rule it had been told about in chat. Both edits were lost: the QA
PAT has no `Contents:write` on either repo, so nothing pushed, and
the next VM rebuild reverted the working tree.

Two failure modes converged:

- The "code changes" wording was narrow enough that the QA bot
  rationalised "this is documentation, not code". An absolute "do
  not edit any file" would have foreclosed it.
- Issues filed by QA went out unassigned. Without an explicit
  always-assign rule and concrete recipe edits, the QA bot omitted
  `--assignee` and the dev queue silently filled with orphaned
  issues that nobody owned.

The originating QA reports (Sautner-Studio-LLC/krill#184, #185) flagged
both behaviours; #36 in this repo is the local tracker that bundles
the doc fix because the file lives here.

## Fix

1. `krill-qa/CLAUDE.md` Role section — replaced *"Do not make code
   changes"* with *"Do not edit any file in either repo — including
   this CLAUDE.md, lesson files, agent prompts, or any source"*, and
   added *"file a `qa-missing-docs` issue against this file instead
   of editing it"*.
2. `krill-qa/CLAUDE.md` Filing-issues section — prepended an
   always-assign paragraph naming `krill-blue-bot`, with both
   `gh issue create --assignee` and REST `assignees:[…]` forms.
3. `krill-qa/CLAUDE.md` both `gh issue create` example recipes (the
   krill bug example and the krill-oss skill-gap example) — added
   `--assignee krill-blue-bot \` between the last `--label` flag and
   the `--body` block. The QA bot copies these recipes verbatim, so
   the flag has to live in the example, not just in the prose.
4. `krill-qa/CLAUDE.md` end-of-file — appended a *Hard rules* section
   in the dev-agent's "don't violate" style, enumerating: no file
   edits, no push/branch/commit/PR, no closing issues except
   self-filed smoke tests or post-verification, no assigning anyone
   but `krill-blue-bot`, no applying umbrella `qa` / `qa-agent`
   labels to issues the bot didn't file, and an explicit
   "if this file looks wrong, file a `qa-missing-docs` issue" escape
   hatch.
5. `krill-mcp/krill-mcp-service/src/test/kotlin/krill/zone/mcp/skill/QaRulesTest.kt`
   — JVM regression test that asserts the loose phrasing is gone,
   the broader phrasing is present, `krill-blue-bot` and the
   `--assignee krill-blue-bot` recipe edit both appear, and the
   *Hard rules* section exists. Mirrors the existing `SkillRulesTest`
   pattern; reaches `../../krill-qa/CLAUDE.md` from the
   krill-mcp-service Gradle working dir.

## Prevention

- Agent-facing prompts must be **absolute**, not advisory. "Do not
  make code changes" leaves room for "this is documentation, not
  code" rationalisations. "Do not edit any file" forecloses the
  category. When the audience is an LLM, words that a human would
  read as obvious shorthand for the broader category may be parsed
  literally — write the literal version.
- When a doc tells the agent to copy a recipe verbatim, the rule
  has to live **in the recipe**, not (only) in the prose two
  paragraphs above. The QA bot's omitted `--assignee` was
  predictable: the example didn't have it, the prose did, the bot
  copied the example. Same pattern as `Closes #<n>` in the PR
  template — if it isn't in the boilerplate, it doesn't ship.
- Defaults that affect every workflow output (assignee, labels,
  template) should be expressed as a *single* rule near the top of
  the relevant section, AND echoed in every concrete example. Two
  copies is intentional redundancy, not duplication.
- A *Hard rules* section at the end of an agent's CLAUDE.md is
  cheap insurance. The dev agent's CLAUDE.md has had one for a
  while; the QA agent should have had one from day one. Mirror
  the pattern across every agent-facing CLAUDE.md.
- Doc-only fixes still get a regression test. The
  `SkillRulesTest`/`QaRulesTest` pattern (read the markdown, assert
  forbidden phrases absent + required phrases present) is fast,
  cheap, and survives reformatting that a substring-grep CI step
  wouldn't.
