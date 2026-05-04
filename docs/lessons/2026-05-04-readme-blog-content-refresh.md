# README drifted from the platform's shipped feature set

**Issue:** [krill-oss#63](https://github.com/Sautner-Studio-LLC/krill-oss/issues/63)
**Root cause category:** Documentation drift — stale top-level entry point
**Module:** repo plumbing (README only)

## What happened

The repo's `README.md` is the first thing any external visitor (and most
internal newcomers) reads. It hadn't been refreshed against the krillswarm.com
blog cadence in months, so the `What Krill Can Do` bullets advertised a much
narrower platform than what actually shipped. Specifically the README was
silent on every feature called out below, despite each having a published
post on `krillswarm.com` (and most having a corresponding JSON spec in
`/home/ben/Code/krill/shared/src/commonMain/resources/`):

- iOS app (released 2026-03-07)
- Raspberry Pi Camera Module 3 nodes (2026-03-30)
- Project Dashboards with Visual / Controls / Data / Hardware / Automation
  sections (2026-04-01)
- Local LLM integration via Ollama (2026-03-20)
- COLOR DataPoint type and the new Color trigger (2026-04-05)
- SMTP email alerts (2026-04-03)
- Diagrams / Journals / Task Lists as project children
- Source-available licensing model and the daily source sync (2026-04-07)
- The fact that this repo also ships an MCP server and Claude skill
  (`krill-mcp/`)

Two link bugs also surfaced while reading the file end-to-end:

- The "Open an issue on GitHub" bullet linked to
  `https://github.com/krill-oss/issues` — that's a bare repo name, not a
  full slug, so it 404s.
- A pinned `**Current version:** 1.0.890` line at the bottom is a manual
  version stamp that goes stale every release. We don't enforce it
  anywhere; the SDK + Pi4J badges at the top track real published versions
  automatically.

The blog post at `/posts/2026/04/07/oss/` also referenced subdirectories
that aren't actually in this checkout (`source/`, `lambdas/`, `svg/` at the
repo root). The real layout has `cookbook/lambdas/` and
`cookbook/SVG Templates/`, and there is no top-level `source/` directory
yet. Repeating the blog's claim verbatim would have shipped four broken
links.

## Fix

1. Rewrote `README.md` end-to-end against the current platform feature set
   sourced from `/home/ben/Code/krill/docs/_posts/` (the krillswarm.com
   Jekyll source) and the `KrillApp.*.json` node-type specs under
   `shared/src/commonMain/resources/`. New sections:
   - Expanded `What Krill Can Do` with cameras, project dashboards, color
     sensing, local LLM, SMTP, MCP-for-Claude, and the iOS app.
   - New `Repository Layout` table mapping each Gradle root + cookbook
     subdir + lessons folder to a one-line description, with verified
     relative links.
   - New `Open Source Philosophy` section paraphrased from the
     2026-04-07 OSS blog post (source-available core, fully open
     `krill-pi4j` / SDK / MCP / cookbook / SVG templates).
   - New `Built On` table credit-linking Kotlin Multiplatform / Compose /
     Ktor / Pi4J / H2 / Mosquitto / Firejail / Ollama.
2. Fixed the broken `https://github.com/krill-oss/issues` link to point at
   `https://github.com/Sautner-Studio-LLC/krill-oss/issues`.
3. Dropped the manual `**Current version:** 1.0.890` footer line. The
   live SonarCloud / Maven Central / Pi4J badges already render up-to-date
   numbers for the things consumers actually pin against; the manual
   stamp had no listener and went stale every release.
4. Verified every relative path linked from the new README exists in the
   current checkout (`pi4j-ktx-service/`, `krill-sdk/`, `krill-mcp/`,
   `krill-mcp/skill/krill/`, `cookbook/lambdas/`, `cookbook/SVG Templates/`,
   `docs/lessons/`). The 2026-04-07 blog post's `tree/main/source` and
   `tree/main/svg` paths are *not* yet present in the public repo, so the
   README references the source sync narratively rather than linking into
   a directory that 404s.

## Prevention

- Tie README refreshes to the existing blog cadence: every time
  `/home/ben/Code/krill/docs/_posts/` gets a post that announces a new
  user-facing capability (camera, LLM, color, etc.), the README's
  `What Krill Can Do` bullet for that area is the canonical
  publish-target for the same change. The `krill-oss` repo already has
  the blog source mounted as a sibling working copy, so an agent can
  always cross-reference without leaving the queue.
- Don't trust narrative claims about repo layout when writing the README.
  Walk the tree (`ls`, `Glob`) and verify each linked path resolves
  before committing — broken relative links rendered on the GitHub web
  view are an easy regression to ship and a hard one to notice locally.
- Avoid manual version stamps in long-lived prose. Maven Central +
  SonarCloud badges are the source of truth for the SDK and Pi4J;
  for the `krill` server and apps, link to the download page rather
  than baking a number into the README that nobody updates.
