# Verify Agent Ghost — allow downgrades when reinstalling QA-stamped debs

**Issue:** [krill-oss#60](https://github.com/Sautner-Studio-LLC/krill-oss/issues/60) (workflow regression on first end-to-end run)
**Introducing commit:** `9aecfef` — "ci: add Dev Agent Blue + Verify Agent Ghost workflows"
**Module:** `module:build-ci` (workflow only — no shipped code touched)

## What happened

The first real end-to-end fire of `Verify Agent Ghost.yml` (against PR #59) failed at the **Install built packages** step:

```
The following packages will be DOWNGRADED:
  krill krill-desktop krill-mcp krill-pi4j
E: Packages were downgraded and -y was used without --allow-downgrades.
```

The ghost runner is a long-lived workstation with the four packages pre-installed once, manually, from the production apt repo. That manual install is *deliberate*: the krill and krill-mcp postinst scripts prompt interactively for a 4-digit cluster PIN on a clean install, and a CI job has no tty to answer the prompt. So the runner is provisioned once with the PIN entered by hand, and verify runs reinstall on top of that base.

Step 3 of the workflow (Stamp build number on deb control files) suffixes each `Version:` field with `+qaPR<n>.r<m>` so successive verify runs sort strictly higher than each other. But the *base* version it stamps onto comes from the source tree's `DEBIAN/control`, which lags the production repo's deb (production keeps shipping while the source-tree base sits between releases). That makes the stamped version (e.g. `1.0.894+qaPR59.r1`) lower than what's already installed (`1.0.999`), and apt rejects the reinstall as a downgrade.

## Fix

- **`.github/workflows/Verify Agent Ghost.yml`** — add `--allow-downgrades` to the `apt-get install` call in the **Install built packages** step. The PIN-prompt avoidance is unaffected: both postinst scripts (`krill-mcp/krill-mcp-service/package/DEBIAN/postinst`, the krill server's equivalent in the sibling repo) check for an existing PIN-derived key file at the start and skip the prompt when found, regardless of whether the install is an upgrade or downgrade.

That's the entire change — one flag.

## Why not bump the source-tree base instead

I considered making step 3 read the currently-installed version (via `dpkg-query -W`) and using *that* as the base it stamps `+qaPR<n>.r<m>` onto, so the result would always sort strictly higher. Rejected:

- It couples the verify workflow to runner state, which is exactly the kind of implicit dependency that breaks when the runner is re-provisioned. `--allow-downgrades` is explicit about what's being permitted.
- It wouldn't help the first-time case where the runner has *no* package installed yet — `dpkg-query` would return empty, and we'd be back to the source-tree base.
- The fix is one flag; the workaround is conditional shell logic in the version-stamping step.

## Why not switch to `dpkg -i`

`dpkg -i` doesn't enforce the upgrade-only invariant, so it bypasses the downgrade error without a flag. Rejected:

- `dpkg` doesn't resolve dependencies. The four debs themselves don't have unresolved deps in practice (the runner already has Java, etc.), but if a future deb adds one, the failure mode would be cryptic — `dpkg` would happily install a half-broken package and leave it to `apt --fix-broken` later.
- `apt-get install` is the canonical install path for these packages on a real user machine; verify runs should mirror that path so the QA agent exercises the same install behavior an end user would hit.

## Sibling drift note

The same pattern exists in `Sautner-Studio-LLC/krill/.github/workflows/Verify Agent Ghost.yml` — same `apt-get install -y` line, same exposure to the downgrade error. This PR fixes only the krill-oss copy because dev-krill-oss owns only this repo. If the ghost runner gets re-provisioned and the krill-side workflow fires before its copy is patched, it will hit the same error.

## Prevention

- **Workflows that reinstall packages on a long-lived self-hosted runner need `--allow-downgrades` whenever the source-tree base version can lag the production-repo version.** The version-stamp suffix (`+qaPR<n>.r<m>`) makes runs strictly increase relative to each other, but it doesn't cover the production-base ↔ source-tree-base lag. If a workflow installs over a pre-provisioned baseline, treat downgrade as a normal case, not an error.
- **Postinst scripts that prompt for secrets must have an "already configured, skip prompt" branch** — both krill and krill-mcp postinst already do, which is what makes pre-provisioning the runner a viable strategy. Don't write a new postinst that prompts unconditionally; the verify path will start failing the moment that package is added to the workflow.
- **First-time end-to-end fire of a new CI workflow is a verification step, not a "should just work" step.** This regression only surfaced because the workflow was actually run against a real PR with the runner in its real long-lived state — a smoke test against a fresh runner wouldn't have caught it. When adding a workflow that depends on host state (pre-installed packages, mounted credentials, persistent caches), treat the first real run as the test.
