# `krill-mcp-token` advertised everywhere but never shipped

**Issue:** none — reported directly by Ben (command not found on a prod install)
**Root cause category:** Packaging — gitignore silently swallowed a deb payload script
**Module:** `krill-mcp`

## What happened

The krill-mcp deb's `postinst` banner, `DEPLOYMENT.md`, `SKILL.md`,
`references/mcp-tools.md`, and two krill-repo blog posts all instruct users to
run `sudo krill-mcp-token` to reprint the Claude connector URL + bearer token.
The script never existed: from the original packaging commit (`3770ea9`),
`DEPLOYMENT.md` included a `chmod 755 .../usr/local/bin/krill-mcp-token` step,
but no such file was ever committed. The likely cause is `krill-mcp/.gitignore`'s
generic Eclipse `bin/` pattern, which matches `package/usr/local/bin/` and
silently ignored the script. The production `Deploy Debian Repo.yml` (krill
repo) then dropped the chmod line — presumably because it failed on the missing
file — so every shipped deb advertised a command it didn't contain.

## Fix

- `krill-mcp/krill-mcp-service/package/usr/local/bin/krill-mcp-token`: new
  POSIX sh script that reprints the postinst banner (connector URLs + bearer).
  Reads `/etc/krill-mcp/credentials/pin_derived_key` and the `listenPort` from
  `/etc/krill-mcp/config.json` (default 50052); paths are env-overridable
  (`KRILL_MCP_KEY_FILE`, `KRILL_MCP_CONFIG_FILE`) so it can be exercised
  against fixtures without touching production paths.
- `krill-mcp/.gitignore`: un-ignore `krill-mcp-service/package/usr/local/bin/`
  while keeping the build-time-copied `krill-mcp.jar` ignored.
- `.github/workflows/Verify Agent Ghost.yml`: chmod the script in the deb
  build step, matching `DEPLOYMENT.md`.
- krill repo (separate change): restored the same chmod line in
  `Deploy Debian Repo.yml`.

## Prevention

- **Never document a command before the file exists in the tree.** If a
  postinst banner or doc names an executable, `git ls-files` must show it.
- **Audit generic IDE gitignore patterns (`bin/`, `out/`, `build/`) against
  deb payload trees.** `package/usr/local/bin/` is payload, not build output;
  a template gitignore can silently drop shipped files. `git status` showing
  clean is not proof a new file was added — use `git check-ignore -v` when a
  payload path overlaps a common ignore name.
- **A failing build step is a signal, not an obstacle.** The production
  workflow omitting `DEPLOYMENT.md`'s chmod line hid the missing file instead
  of surfacing it. When a documented build step fails, fix the cause rather
  than deleting the step.
