# Release-train workflows: source the runner PAT, don't expect a secret

**Root cause category:** CI/CD — wrong token source for self-hosted kraken-runner jobs
**Module:** repo plumbing (CI)

## What happened

The release-train workflows (`release-pr-update`, `pr-risk-classify`,
`release-notes`, `hotfix-cherrypick`) were authored with
`env: GH_TOKEN: ${{ secrets.KRILL_KRAKEN_BOT_PAT }}`. That secret does **not
exist** — the self-hosted kraken/blue/ghost runners get `krill-kraken-bot`'s PAT
from **`/etc/environment`**, the same way Nightly Bug Hunt / Dev Agent Blue /
Nightly UX Audit do. The empty secret expression resolved to an empty string and
*shadowed* the inherited `GH_TOKEN`, so `gh` failed (`set the GH_TOKEN
environment variable`) and `update-release-pr.py` logged "no open integration PR"
and made no change — the integration PR body never regenerated.

## Fix

- Removed every `secrets.KRILL_KRAKEN_BOT_PAT` reference.
- Each `gh`/`git push` step now does `set -a; source /etc/environment 2>/dev/null
  || true; set +a` so the runner's `GH_TOKEN` (the bot PAT) is exported to `gh`
  and `uv run python` subprocesses.
- `hotfix-cherrypick` moved from `ubuntu-latest` to the kraken runner and points
  `origin` at a token https remote, so the cherry-pick PR's push triggers CI (a
  default `GITHUB_TOKEN` push would not, stalling automerge).

## Prevention

- Self-hosted-runner jobs authenticate `gh`/`git` from `/etc/environment`
  (`krill-kraken-bot` PAT), never from an Actions secret. Copy the Nightly Bug
  Hunt idiom, don't invent a secret name.
- Setting `env: GH_TOKEN: ${{ secrets.MISSING }}` is worse than setting nothing —
  it overrides the inherited value with empty. Omit it, or source the real one.
- A PAT (not `GITHUB_TOKEN`) is required wherever a push must trigger downstream
  workflows (CI on a bot-opened PR, the `assigned` event for Blue).
