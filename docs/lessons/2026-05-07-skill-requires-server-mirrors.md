# Skill node-type mirrors lacked `requiresServer` for Journal / TaskList / DataPoint

**Issue:** [krill-oss#68](https://github.com/Sautner-Studio-LLC/krill-oss/issues/68)
**Upstream:** Phases 3 / 4 / 5 of `local-first-onboarding` — [krill#258](https://github.com/Sautner-Studio-LLC/krill/issues/258), [krill#259](https://github.com/Sautner-Studio-LLC/krill/issues/259), [krill#260](https://github.com/Sautner-Studio-LLC/krill/issues/260)
**Root cause category:** Cross-repo mirror drift — the bundled skill's node-type JSONs were copied from an older snapshot of `krill/shared/src/commonMain/resources/` and had not been refreshed when upstream added the `requiresServer` field
**Module:** `module:krill-skill`

## What happened

The `krill-mcp/skill/krill/references/node-types/*.json` files are mirrors of the canonical node-type specs in the private `krill` repo. Upstream added a `requiresServer: Boolean` field to every `KrillApp.*.json` (krill#241) and is now relaxing that flag to `false` for `KrillApp.Project.Journal`, `KrillApp.Project.TaskList`, and `KrillApp.DataPoint` so the apps can host them locally without a Krill server. The skill mirrors had not yet been refreshed for the field at all, so an agent reading them got an out-of-date picture of which node types are server-dependent.

## Fix

- `krill-mcp/skill/krill/references/node-types/KrillApp.Project.Journal.json` — added `"requiresServer": false`.
- `krill-mcp/skill/krill/references/node-types/KrillApp.Project.TaskList.json` — added `"requiresServer": false` and appended a local-hosting note to `description`: "Locally-hosted task lists do not fire executors or send notifications. Move the list to a Krill server for those behaviors." Matches the downstream description so an agent reading the mirror sees the same behavioral caveat the app surfaces.
- `krill-mcp/skill/krill/references/node-types/KrillApp.DataPoint.json` — added `"requiresServer": false`. The other ~30 mirrored node-type JSONs still lack the field; resyncing them is out of scope for this issue (they're not part of the local-first phase work).

No code change. The skill loads these JSONs as plain text references for the LLM, not via a typed deserialiser, so adding the field is purely additive — there is no consumer that would break on its presence.

## Prevention

- **Treat the skill node-type mirrors as a downstream of `krill/shared/src/commonMain/resources/`, not as a parallel source of truth.** When upstream adds a field, the mirrors should follow in a paired krill-oss PR — just like `KrillFeature` in `krill-sdk` (see `2026-05-07-feature-requires-server-field.md`). Without a forcing function, the mirror drifts silently because nothing in the build links the two trees.
- **Resync rather than hand-patch when the mirror is multiple revisions stale.** This PR is a targeted three-file fix because the issue's acceptance criteria are scoped that way, but the broader drift (other ~30 node types missing `requiresServer`) is real and should land as a separate sweep so the diff is reviewable on its own terms.
- **Skill-bundle changes are agent-runtime changes, not docs.** The `needs-qa-verify` decision rule in `shared/workflow.md` already calls this out; the lesson here is to keep applying it for content that the skill loads at agent runtime even when the diff looks like prose.
