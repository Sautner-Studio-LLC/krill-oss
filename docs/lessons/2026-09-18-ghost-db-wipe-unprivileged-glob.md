---
issue: Sautner-Studio-LLC/krill-oss#262
pr: Sautner-Studio-LLC/krill-oss#263
date: 2026-09-18
module: other
category: ci-cd
title: "Verify Agent Ghost's DB-wipe step silently no-op'd, leaving weeks-old nodes in a \"fresh\" swarm"
description: "sudo rm -f with a shell glob over a 0750 root-owned directory silently deleted nothing — a QA verify run's DB wipe left prior test fixtures behind."
tags: ["ci-cd", "github-actions", "sudo", "glob", "silent-failure", "verify-agent-ghost"]
---

## What happened

`Verify Agent Ghost.yml`'s "Wipe local krill DB and restart krill.service" step ran
`sudo -n rm -f /srv/krill/data/*.db` before restarting `krill.service`, intending to give
every QA verify run a genuinely empty swarm. Instead, Ghost's `list_nodes` on first contact
after a "fresh" restart returned 32 pre-existing nodes — including two whose IDs matched
fixtures created during a *different* PR's verify run three weeks earlier. `krill.service`'s
`ActiveEnterTimestamp` confirmed the process really had restarted and was running the new PR's
build, so the binary/restart machinery worked; only the database state failed to reset.

Root cause: `/srv/krill/data` is created `0750 krill:krill` by the server's `postinst`
(`install -d -o krill -g krill -m 0750 /srv/krill/data`), and the runner user is never added
to the `krill` group — `postinst`'s `adduser` calls only ever add the `krill` system user
itself to supplementary groups. A glob in `sudo -n rm -f /srv/krill/data/*.db` is expanded by
the *calling*, unprivileged shell before `sudo` ever runs — `sudo` only gains root for the
command it execs, not for the shell that built its argument list. Against a directory the
caller can't read, that glob silently fails to expand and `rm -f` receives the literal,
nonexistent filename `*.db`; `-f` swallows the resulting error. The step logged success and
deleted nothing.

## Fix

- `.github/workflows/Verify Agent Ghost.yml`: wrap both the delete and a post-wipe check in
  `sudo -n bash -c '...'` so the glob expands inside a root-invoked shell that can actually
  read the directory, then count any surviving `*.db` files with `nullglob` and hard-fail the
  step (`exit 1` with an `::error::` annotation and a `sudo -n ls -la` dump) if any remain —
  turning a future regression into a loud CI failure instead of a silent no-op.
- Filed the identical bug against `Sautner-Studio-LLC/krill`'s copy of the same workflow file
  (`krill#<TBD>` — same content, same fix needed, out of scope for this repo's PR).

## Prevention

- **A `sudo rm -f <glob>` over a directory the calling shell can't read is not evidence the
  files were removed.** The glob expansion happens in the caller's shell, not under `sudo`;
  `-f` exists specifically to suppress "no such file" errors, which means it also suppresses
  the signal that the glob never matched anything. Any privileged deletion of files the
  unprivileged caller can't list should expand the glob (or otherwise enumerate the target)
  inside the `sudo`'d shell — `sudo -n bash -c 'rm -f /path/*.ext'`, not
  `sudo -n rm -f /path/*.ext`.
- **A wipe/reset step meant to establish a known-empty precondition should assert the
  postcondition, not just run the delete and move on.** Counting remaining matches after the
  delete (as this fix does) converts "we ran a command that we believe deletes things" into
  "we verified nothing matching survived" — the gap between those two is exactly where this bug
  lived, undetected, for at least three weeks of verify runs.
