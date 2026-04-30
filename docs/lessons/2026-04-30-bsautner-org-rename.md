# Stale `bsautner/*` org slugs survived the rename to `Sautner-Studio-LLC`

**Issue:** [krill-oss#38](https://github.com/Sautner-Studio-LLC/krill-oss/issues/38)
**Root cause category:** Repo plumbing — incomplete org rename
**Module:** repo plumbing (workflows, templates, lessons, tests, Maven POM)

## What happened

Both repos moved from `bsautner/*` to `Sautner-Studio-LLC/*` on
2026-04-30. Issue #36 (PR #37) tightened the QA agent's role and
left "the wider rename" as a follow-up, which became #38. Ten files
still carried legacy old-org repo slugs (`bsautner/<repo>`) — concrete
references to the old GitHub paths. Nine were updated in this PR;
the tenth (`.github/workflows/release-sdk.yml`) requires a follow-up
PR Ben pushes himself because the `krill-blue-bot` PAT lacks the
`workflow` scope GitHub demands for any push that touches a workflow
file. The release-sdk dispatch is operationally live — the next
krill-sdk version bump on `main` would silently no-op (the dispatch
target slug no longer hosts the publish workflow), so a fix would
land in this repo with no Maven Central artifact (same failure shape
as lesson #17). Files updated in this PR:

- `pi4j-ktx-service/krill-pi4j/build.gradle.kts` — POM `url` and
  three SCM URLs. These are baked into every published `krill-pi4j`
  artifact for the lifetime of that version; consumers and IDE
  source-jumpers read them, so a stale slug there is locked in on
  Maven Central until the next release.
- `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/dev-task.yml`
  — agent-facing copy that the dev bot reads on every PR / issue.
- `docs/lessons/2026-04-2[67]-*.md` — issue links and prose. GitHub
  redirects the old slugs, but the lessons should match the current
  org for grep hygiene.
- Two test KDoc lines in `CreateNodeToolTest.kt` and
  `SkillRulesTest.kt` referencing the old issue tracker slug.

Two related side effects of the consolidation in PR #42 also surfaced:

- `krill-qa/CLAUDE.md`, `krill-mcp/CLAUDE.md`, and the top-level
  `CLAUDE.md` were deleted in favour of the auto-generated
  `CLAUDE.md` produced by `krill-agents/scripts/build-claude-md.sh`.
  The QA bot's CLAUDE.md now lives only in the `krill-qa` sandbox
  (`/home/ben/Code/krill-qa/`), not in this repo.
- `QaRulesTest.kt` (added in PR #37 by issue #36) reads
  `../../krill-qa/CLAUDE.md`. PR #42 deleted that file, leaving the
  test guaranteed to fail with `FileNotFoundException` on every
  `:krill-mcp-service:test` run. CI on `main` was masking it because
  the self-hosted runners had been cancelling builds, but the next
  green run would have been red.

## Fix

1. **Workflow dispatch** — `.github/workflows/release-sdk.yml`
   *deferred to a follow-up PR Ben pushes manually*: GitHub
   rejects any push to a `.github/workflows/*` file from a token
   without `workflow` scope, and `krill-blue-bot`'s PAT doesn't
   carry that scope (deliberately — minimum-privilege). The
   `OrgReferencesTest` regression test below carries a `PENDING_PATHS`
   whitelist for this single file so CI stays green; remove the
   whitelist entry in the same PR that updates the workflow. The
   `KRILL_PUBLISH_TOKEN` secret on this repo also needs to be
   re-scoped to the new org's `krill` repo — also an ops action.
2. **Maven POM** — `pi4j-ktx-service/krill-pi4j/build.gradle.kts`
   `pom.url` and all three `scm.*` URLs flipped to the new org. The
   developer `id = "bsautner"` (Ben's GitHub username) stays — that
   field is per-person, not per-org.
3. **Templates** — `PULL_REQUEST_TEMPLATE.md` (3 lines) and
   `dev-task.yml` (1 line) updated. These are the boilerplate the
   dev bot copies verbatim, so they have to match the live org.
4. **Lessons + test KDocs** — six remaining markdown / KDoc lines
   updated. The `bsautner/*` wildcard literal in
   `2026-04-30-qa-agent-role-tightening.md` is left as-is: it's
   historical narrative referring to stale wildcard paths, not a
   live slug.
5. **Tangential cleanup** — deleted
   `krill-mcp/krill-mcp-service/src/test/kotlin/krill/zone/mcp/skill/QaRulesTest.kt`.
   The file it reads (`krill-qa/CLAUDE.md`) was deleted by PR #42
   in favour of the auto-generated CLAUDE.md, leaving the test
   permanently red. The QA agent's CLAUDE.md now lives in the
   `krill-qa` sandbox — outside this repo's scope and not validated
   from here. Removing dead test code rather than re-pointing it.
6. **Regression test** —
   `krill-mcp/krill-mcp-service/src/test/kotlin/krill/zone/mcp/skill/OrgReferencesTest.kt`
   walks the repo root, scans every text file, and asserts no
   concrete old-org slug survives. The forbidden substring is built
   at runtime so the test source isn't a self-match, and the test
   file itself is excluded from the walk for safety. Wildcard-form
   historical narrative (asterisk-suffixed) is intentionally allowed.

## Prevention

- An org rename is multi-site by definition — workflow files,
  Maven POMs, agent-facing templates, and historical lesson links
  all need to flip together. A grep-driven regression test
  (`OrgReferencesTest`) is the cheap insurance: any future drift
  will fail CI on the krill-mcp build.
- POM URLs are an "out-of-band" surface — they don't break the
  build, but they ride along on every published artifact. Treat
  any change to `pom.url` / `scm.*` as load-bearing on the next
  Maven Central publish, not as cosmetic.
- Cross-repo workflow dispatches (`gh workflow run --repo X/Y`)
  fail silently when the slug is wrong: the dispatch returns an
  HTTP error, but the workflow still reports success at the call
  site if you only check the exit code of the dispatch step in
  some configurations. After an org rename, run the dispatch
  end-to-end (or re-trigger the next version bump deliberately) to
  confirm it lands.
- When a PR deletes a file that another PR's test depends on, the
  test goes from passing to throwing `FileNotFoundException` — but
  Gradle's test runner reports that as a normal test failure, so a
  cancelled or queued CI run can mask it indefinitely. After any
  consolidation that deletes files, run `./gradlew check` from
  every Gradle root locally before merging.
- Tests that grep their own source need a self-exclusion: either
  build the forbidden substring at runtime (string concat) or skip
  the test file's canonical path during the walk. Doing both is
  cheap belt-and-braces.
