# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Role

QA tester for the Krill platform. Reproduce issues, gather evidence, and file GitHub issues against the correct repo using the correct template and labels. **Do not make code changes** in either repo — observe, file, and stop.

This directory is an intentionally empty scratch sandbox. Do not pre-load krill domain knowledge from memory or the repos — interact with a running swarm only through the `krill` skill and its MCP server, so first-time-user friction surfaces naturally.

## Repos under test

A separate dev agent works each repo. Filing into the wrong repo silently routes the bug to the wrong dev — be deliberate.

| Path | GitHub | Owns |
|---|---|---|
| `/home/ben/Code/krill` | `bsautner/krill` (private) | Krill Server (Ktor + DB + SSE), Compose App (android/ios/desktop/web), shared KMP modules, KSP, build/CI, openspec |
| `/home/ben/Code/krill-oss` | `bsautner/krill-oss` (public) | `krill-sdk`, `krill-mcp` (the MCP server itself + its bundled `krill-skill`), `krill-pi4j` (client lib + daemon), cookbook lambdas, SVG templates |

### Repo-selection rule

Ask: **"where is the broken code?"**, not "what was I doing when it broke."

| Symptom you observed | Goes to |
|---|---|
| MCP tool returned wrong/missing data, error message, or shape | `krill-oss` (`module:krill-mcp` or `module:krill-sdk`) |
| Skill instructions are wrong, missing, or led you astray | `krill-oss` (`module:krill-skill`) |
| Hardware GPIO/PWM/I2C call misbehaved on the Pi | `krill-oss` (`module:krill-pi4j`) |
| Cookbook lambda example doesn't run | `krill-oss` (no module label yet — note in body) |
| Krill server returned wrong data over HTTP/SSE, or DB state is wrong | `krill` (`module:server`) |
| Compose UI bug (desktop/web/Android/iOS) | `krill` (`module:composeApp` + the target shell label if relevant) |
| Shared model / serialization / DI bug visible in both server and app | `krill` (`module:shared`) |
| Build, Gradle, CI, packaging | the repo whose build is broken |

If the symptom is in MCP/skill/SDK output but you suspect the *root cause* is in the server, file in `krill-oss` with what you observed and add a one-liner in the body: *"Possibly upstream in krill server — see logs."* Don't try to diagnose across repos in one issue.

## Filing issues

Use `gh issue create --repo <repo> --template <template>` — never freeform. Both repos expose the same four QA templates:

- `qa-bug.yml` — incorrect behavior, crash, wrong output.
- `qa-friction.yml` — works but is confusing, slow, or awkward.
- `qa-missing-docs.yml` — behavior is undocumented or docs are wrong.
- `qa-skill-gap.yml` — the `krill` skill failed to guide a first-time user (missing trigger, wrong tool choice, dead end). **This is the primary reason this sandbox exists** — file generously here.

### Required labels per template

Template `labels:` auto-apply only when an issue is filed through the GitHub web UI. When you file via `gh issue create --label ...` you **must pass every label explicitly**.

The umbrella label differs per repo (historical — don't try to "fix" it):

- **krill** → `qa-agent`
- **krill-oss** → `qa`

All other type labels are the same on both repos:

| Template | Umbrella | Plus type labels |
|---|---|---|
| `qa-bug.yml` | `qa-agent` or `qa` | `bug` |
| `qa-friction.yml` | `qa-agent` or `qa` | `friction`, `dx` |
| `qa-missing-docs.yml` | `qa-agent` or `qa` | `documentation` |
| `qa-skill-gap.yml` | `qa-agent` or `qa` | `skill-gap`, `agent-tooling` |

On top of those, always add:

- **One `module:*` label** for where the defective code lives.
  - **krill** modules: `module:server`, `module:shared`, `module:composeApp`, `module:androidApp`, `module:iosApp`, `module:ksp`, `module:build-ci`, `module:openspec`. (`module:krill-mcp` and `module:krill-pi4j` also exist on krill, but those issues almost always belong on **krill-oss** — re-check repo choice before using.)
  - **krill-oss** modules: `module:krill-sdk`, `module:krill-mcp`, `module:krill-skill`, `module:krill-pi4j`.
  - When unsure: `gh label list --repo <repo> --search module:`.
- **One `severity:*` label for every `qa-bug`** (both repos) — `severity:blocker` / `severity:high` / `severity:medium` / `severity:low`. Optional but encouraged on friction/skill-gap to help dev agents prioritize.

### Body schema differs per repo

The two repos' QA templates ask for **different fields**. Don't write the body from memory — read the template you're filing under and mirror its `body:` section IDs as Markdown headers in `--body`. The dev agent expects the same shape they'd see in the web form.

```bash
# Inspect the template's fields before composing the body:
cat /home/ben/Code/krill/.github/ISSUE_TEMPLATE/qa-friction.yml
cat /home/ben/Code/krill-oss/.github/ISSUE_TEMPLATE/qa-friction.yml
```

Before filing, dedupe: `gh issue list --repo <repo> --search "<keywords>" --state all`. If it exists, comment on the existing issue instead.

### Example: filing on krill (server/app side)

```bash
gh issue create --repo bsautner/krill \
  --title "[QA-Bug] Server returns 500 on /api/node POST when meta.target missing" \
  --label qa-agent --label bug --label module:server --label severity:high \
  --body "$(cat <<'EOF'
### Task you were asked to do
Create a Thermostat node via the krill skill.

### Surface
server

### Suspected module
module:server

### Node type involved (if any)
Thermostat

### Reproduction steps
1. …
2. …

### Expected behavior
…

### Actual behavior
HTTP 500 with stack trace …

### Relevant logs / output
…

### Build / version
1.0.888

### Severity
high
EOF
)"
```

### Example: filing on krill-oss (MCP/skill/SDK side)

```bash
gh issue create --repo bsautner/krill-oss \
  --title "[skill-gap] No MCP tool to list valid parent node ids for a given child type" \
  --label qa --label skill-gap --label agent-tooling --label module:krill-mcp \
  --body "$(cat <<'EOF'
### Task you were asked to do
Create a Trigger under host `pi-krill` using only the krill skill.

### Suspected module
krill-mcp (server)

### What was missing
…

### What you tried
- …
- …

### Workaround / where you stopped
…

### Proposed fix
A new MCP tool `list_valid_parents(childType)` returning candidate node ids.
EOF
)"
```

## Evidence to capture

- Exact MCP tool calls made and their responses (or skill steps followed).
- What the skill/docs said to do vs. what actually happened.
- Krill server version / host if working against a live swarm.
- Minimal repro — the shortest sequence that triggers it.
