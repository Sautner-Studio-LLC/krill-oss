## Context

`krill-sdk` ships as a Kotlin Multiplatform library on Maven Central. Its only existing test surface is `commonTest` (JVM-runnable), built around fakes — there is **no** automated check that an installed Krill server speaks the same wire protocol the SDK emits. Wire-level regressions (JSON shape drift, broken auth handshake, snapshot reorder, filter mis-evaluation) are caught manually by the QA agent days after they land on `main`.

A house Krill server already runs at `https://pi-util-01:8442` in always-on mode. It is the same image deployed downstream, exposes a self-signed TLS cert, and is reachable from Ben's LAN. We can bolt a live-test contract onto it cheaply: run the SDK against it whenever `main` advances, report wire failures to the dev agent's loop, and keep `check` hermetic so day-to-day work doesn't depend on the network.

Stakeholders: this dev agent (consumer of CI signal); QA agent (would otherwise file the same regressions by hand); Ben (decides what blocks releases).

## Goals / Non-Goals

**Goals:**
- Run an end-to-end test suite from `krill-sdk` against a real, configurable Krill server on every push to `main`.
- Cover *every* `KrillApp` leaf node type with at least one isolated CRUD test, plus a composite end-to-end scenario.
- Verify filter and trigger evaluation by injecting deterministic data and asserting expected fan-out.
- Verify high-frequency snapshot durability: ingest at sustained rate, read back, assert ordered, complete, unmodified.
- Keep `./gradlew check` hermetic — live tests opt-in only, never on the default path.
- Keep the suite re-runnable forever against the same long-lived server: every run cleans up after itself even on failure.

**Non-Goals:**
- Spinning up a Krill server inside CI (containerised or otherwise). The whole point is to test a real, persistent install.
- Authentication flows beyond what the existing SDK already supports — we reuse the SDK's auth path; we do not redesign it.
- Performance benchmarking with reportable numbers. Load tier asserts *correctness* under load, not throughput SLOs.
- Multi-server federation tests, peer discovery beacons, or hardware-dependent paths (Pi4J GPIO). Those need real hardware and live elsewhere.
- Replacing the QA agent. This catches a narrow class of wire regressions; QA still owns broader scenario coverage.

## Decisions

### D1. Test code lives in a dedicated JVM source set, not a new subproject

**Choice:** Add a Gradle source set `jvmLiveTest` to `krill-sdk` (sibling to `jvmTest`). Wire it through Kotlin's KMP target DSL: `kotlin.jvm().compilations.create("liveTest")` plus a matching `Test` task `liveTest`.

**Why:** Keeps the suite inside the module under test (no extra `settings.gradle.kts` entry, no inter-module dep), shares `commonMain` types and serialization registrations for free, and keeps publishable artifacts unaffected because the source set never enters `main`. A separate source set (vs. JUnit tags inside `jvmTest`) means the live tests can never accidentally run during `./gradlew check` — the source set isn't compiled by `jvmTest`.

**Alternatives considered:**
- *Tagged tests inside `jvmTest`*: rejected — every contributor would have to remember `--exclude-tags live`, and a missing tag silently makes the whole `check` build network-dependent.
- *New Gradle subproject `krill-sdk-live-tests`*: rejected — duplicates serialization wiring, complicates publish flow, adds a top-level entry no one else needs.

### D2. JVM-only; no KMP test fan-out

**Choice:** The live source set targets the JVM only. No iOS / Android / wasmJs equivalents.

**Why:** The contract under test is the SDK's wire encoding, which is identical on every target because the `commonMain` Ktor client is the only path. Running the same suite on iOS or wasmJs would re-test the same JSON over a different runtime with no extra signal, but cost CI complexity and runner setup. JVM is the path of least resistance and the one CI already supports.

**Alternative considered:** Common test source set with platform actuals. Rejected because the only actual-divergence we'd test is platform HTTP plumbing — and that's better tested with the existing fast `jvmTest` fakes plus targeted unit tests, not a wall-clock-dominated live suite.

### D3. Three tiers, single Gradle task, JUnit 5 tags for sub-selection

**Choice:** One Gradle task `:krill-sdk:liveTest` runs everything. JUnit 5 tags `smoke`, `sanity`, `load` let CI or a local dev pick a tier with `--tests` or `-PliveTier=smoke`. Default `liveTest` invocation runs all three.

**Why:** Single-task simplicity for CI; tags give the dev agent a fast `:krill-sdk:liveTest -PliveTier=smoke` (~10s) when triaging. Tags are JUnit-platform-native, so no custom test infrastructure.

### D4. Server URL and credentials are externally configured, with a sane default

**Choice:** Configuration order of precedence:
1. Env var `KRILL_LIVE_BASE_URL` (CI primary mechanism).
2. Gradle property `-PkrillLiveBaseUrl=...`.
3. Default `https://pi-util-01:8442`.

Same precedence for `KRILL_LIVE_AUTH_TOKEN` (or whatever the SDK's auth path needs). Loaded once into a `LiveServerConfig` data class read at suite startup; if the URL is unreachable the suite fails fast with a clear message rather than per-test timeouts.

**Why:** Env var first matches CI conventions; Gradle property is friendlier locally; baked-in default means `./gradlew :krill-sdk:liveTest` Just Works on Ben's LAN with no flags.

### D5. Self-signed TLS is accepted only for the live source set

**Choice:** The Ktor `HttpClient` configured for live tests installs a `TrustManager` that accepts the cert chain `pi-util-01` presents (or any cert whose CN matches the configured host) — but **only** in the `jvmLiveTest` HTTP factory. The SDK's published client retains its standard validation.

**Why:** The pi runs a self-signed cert; reissuing through a real CA is out of scope. We isolate the trust shortcut to a path that never ships, so the SDK's security posture is unchanged. The factory lives in the live source set, not in `commonMain` or `jvmMain`.

**Risk noted:** see R2 below.

### D6. Per-run namespace prefix for cleanup

**Choice:** Each run computes `runId = "lt-${shortUlid}"` at suite start. Every node the suite creates is named `<runId>/<purpose>` (e.g. `lt-01HX/datapoint-deadband-1`). A `@AfterAll` global teardown deletes every node whose name starts with `lt-` *and* whose creation timestamp is older than a grace window — so abandoned nodes from crashed prior runs get reaped on the next run too.

**Why:** Lets the live server live forever with bounded state. The "older than grace window" check avoids two concurrent runs stomping each other's in-flight nodes (defensive — we don't expect concurrency, but cheap to add).

**Alternative considered:** Wipe everything matching `lt-*` unconditionally. Rejected — fragile if anyone ever runs two tiers in parallel for a debug pass.

### D7. Composite scenario shape

**Choice:** The composite test creates this graph and asserts behaviour end to end:

```
DataPoint(value sensor)
  → DataPoint.Filter.Deadband (epsilon=1.0)
  → Trigger (threshold > 50)
  → Executor.LogicGate (AND-ed with a second DataPoint)
  → Executor.OutgoingWebHook (posts to a sink we control inside the test)
```

The test ingests a scripted snapshot sequence and asserts the webhook sink received exactly the expected hits, in order, with the expected payloads.

**Why:** Touches every layer the SDK contract exposes (data ingest → filter → trigger → executor → side effect) in one scenario, so a wire break anywhere lights this up. Webhook sink is an in-process Ktor server bound to `0.0.0.0:0` and registered with the live server via the OutgoingWebHook node — no external dependencies.

### D8. Load tier: 100 snapshots/sec for 60s on a single DataPoint, then read-back assertion

**Choice:** A coroutine producer emits 100 snapshots/sec (10ms cadence, jittered ±2ms) for 60 seconds against one freshly-created DataPoint, recording a local "expected" sequence in memory. After ingest finishes the test polls until the server's snapshot count equals 6,000, then fetches the full series and asserts:
- Count exactly matches sent count.
- Timestamps are strictly increasing.
- Values match the local expected sequence index-for-index.

If the server's storage layer caps out below 100/sec, the test fails with measured throughput and the highest sustained rate observed — useful debugging output, not a benchmark.

**Why:** 100/sec × 60s is a realistic high-frequency sensor profile, well within what an embedded Krill install should sustain, and produces a series long enough to catch reorder / drop bugs without bloating CI time. Read-back-assertion (vs. just success-on-write) is the only way to catch silent corruption, which is the failure class the user explicitly called out.

### D9. CI runner placement is an open question — first cut uses a self-hosted runner on Ben's LAN

**Choice (provisional):** Initial workflow assumes a self-hosted GH Actions runner reachable from `pi-util-01`. The workflow is gated on the `self-hosted` label so cloud runners don't fail with timeouts.

**Why provisional:** Cloud runners can't reach `pi-util-01` without a tunnel (Tailscale, ngrok, Cloudflare Tunnel). A self-hosted runner is the simplest path to "it works"; the alternative is a tunnel and exposing the pi to the public internet, which is a bigger decision. Flagged in Open Questions.

### D10. CI failure does not block `main`; it pages the dev agent

**Choice:** The workflow runs *on* `push: branches: [main]` (post-merge), reports a status check, and on failure (a) writes a step summary listing failing tests and the request/response from the first failure, (b) opens or comments on a GitHub issue labelled `live-test-failure` so the dev agent picks it up the next time it runs the triage loop.

**Why:** We can't gate merges on a network-dependent suite — `pi-util-01` going down would block the team. Post-merge with auto-issue-filing turns it into the same kind of signal QA gives us, just faster.

## Risks / Trade-offs

- **R1: Live server unavailable** → CI flakes red on every push.
  *Mitigation:* suite fails fast with a distinct exit message ("UNREACHABLE: <url>") that the workflow detects and demotes to a `live-test-infra-down` label rather than `live-test-failure`, so the dev agent doesn't waste a triage cycle on a power-outage day. Also: a 5-minute reachability retry before the suite proper.

- **R2: Trusting a self-signed cert in test code** → habit creep into production code paths.
  *Mitigation:* the trust-shortcut HTTP factory lives only in `jvmLiveTest`, with a comment explaining why it's there. A markdown sanity test (counterpart pattern from `docs/lessons/`) asserts that `commonMain` and `jvmMain` do not import the trust-all factory class — guardrails against future drift.

- **R3: State bleed across runs** → flaky tests caused by leftover nodes.
  *Mitigation:* see D6. Plus a smoke test that asserts the run starts with zero `lt-*` nodes whose age exceeds the grace window — if it fails we surface it loudly instead of silently inheriting stale state.

- **R4: Load tier inflates wall-clock** → contributors stop running it locally.
  *Mitigation:* `-PliveTier=smoke,sanity` cuts the suite to ~30–60s. CI runs the full tier; locally, the dev agent uses `smoke,sanity` for triage and `liveTest` only for load-related fixes.

- **R5: Self-hosted runner is a SPOF** → if Ben's LAN runner is offline, no CI signal at all.
  *Mitigation:* live-test failure does not block other workflows. We accept the SPOF for now; a cloud runner with Tailscale is a follow-up if the SPOF bites.

- **R6: A single composite scenario can't cover all interesting node combinations.**
  *Mitigation:* explicit scope. Sanity tier covers each leaf type in isolation; the composite is one canonical happy-path graph. New cross-type bugs land as targeted regression tests in `commonTest` (hermetic) plus, if they need a real server, an additional sanity case — not a sprawling combinatorial matrix.

## Migration Plan

This is additive — no migration. Rollback is `git revert` on the change PR; nothing about it persists outside the workflow file, the new source set, and the lessons doc.

Deploy steps:
1. Land the source set + suite + workflow in one PR.
2. Manually trigger the workflow on the PR branch (`workflow_dispatch`) to confirm green against `pi-util-01` before merge.
3. Merge to `main`. First post-merge run is the canonical baseline.
4. Watch one or two real `main` pushes after merge; tune flake-prone tests if any.

## Open Questions

- **OQ1:** Self-hosted runner on Ben's LAN, or cloud runner with a tunnel into `pi-util-01`? Provisional answer in D9 is self-hosted; needs Ben's call before the workflow PR can land.
- **OQ2:** Does `pi-util-01:8442` enforce auth today? If yes, what's the credential shape — `PinDerivation` HMAC, bearer token, or something else? The SDK's existing client should already know; need to read `krill-sdk/src/commonMain/kotlin/krill/zone/shared/security/` and the live server config to confirm before writing the suite.
- **OQ3:** When (if ever) should live-test failures block the next release? Out of scope for this change but flagged so we don't forget.
- **OQ4:** Camera and Backup nodes may need fixtures (a real camera URL, a writable backup target) we don't have on `pi-util-01`. Acceptable to skip with `@Disabled("requires fixture")` and file follow-up issues, or do we hard-require coverage on day one? Provisional answer: skip-with-issue is fine; the proposal claims "every node type" which we'll honour with explicit `@Disabled` reasons rather than silent gaps.
