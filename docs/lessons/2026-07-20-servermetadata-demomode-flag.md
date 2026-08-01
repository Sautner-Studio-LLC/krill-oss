---
issue: Sautner-Studio-LLC/krill-oss#208
pr: Sautner-Studio-LLC/krill-oss#209
date: 2026-07-20
module: krill-sdk
category: api-design
---

## What happened

The `krill` server gained a public read-only **demo mode**
(Sautner-Studio-LLC/krill#915) so it can be exposed safely at
`live.krillswarm.com`. When enabled, the server hard-blocks all mutations,
opens reads without a PIN, and redacts secrets. But clients had no way to *know*
they were talking to a demo host — the Compose/WASM client needs that signal to
skip FTUE/PIN onboarding and hide edit affordances (Save/Add/Delete).

`ServerMetaData` (returned by `nodeHttp.readHealth()` / `GET /health`) is the
canonical channel for a server to advertise capabilities to every client — it
already carries `loggingEnabled` and `beaconsEnabled` booleans — but it lived in
`krill-sdk` and had no demo flag.

## Fix

Added `demoMode: Boolean = false` to `ServerMetaData`
(`krill-sdk/src/commonMain/.../krillapp/server/ServerMetaData.kt`), mirroring the
existing capability-boolean pattern, and bumped `krill-sdk` `0.0.59 → 0.0.60`
per the SDK versioning rule. Regression test
`ServerMetaDataDemoModeTest` covers the default, a JSON round-trip, and
back-compat (old payloads without the field deserialize to `false`).

Downstream (separate PRs, gated on this artifact publishing to Maven Central):
- `krill` server populates `ServerMetaData.demoMode` in
  `ServerIdentity.serverMetaData()`.
- `krill` client (`composeApp`) reads it on connect to drive FTUE-skip + edit
  gating.

## Prevention

- **Advertise server capabilities through the one typed health payload, not
  ad-hoc side channels.** `ServerMetaData` already had the pattern
  (`loggingEnabled`); a new boolean field is backward-compatible because
  `fastJson` ignores unknown keys and the default covers old servers — so a new
  client against an old server, and an old client against a new server, both
  work. (The server side added a stopgap unauthenticated `GET /demo` endpoint to
  unblock itself before this artifact ships; that can be retired once every
  client reads `ServerMetaData.demoMode`.)
- **Bump the `krill-sdk` patch in the same PR that touches `krill-sdk/**`** —
  CI dispatches the Maven publish on the version change; skipping it silently
  leaves the field out of Maven Central while the build still passes.
