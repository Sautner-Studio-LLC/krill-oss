## 1. Open questions resolved before coding

- [ ] 1.1 Confirm with Ben: self-hosted runner on LAN vs cloud runner + tunnel (resolves D9 / OQ1)
- [ ] 1.2 Confirm auth scheme on `pi-util-01:8442` and where credentials come from in CI (resolves OQ2)
- [ ] 1.3 Confirm Camera + Backup node fixture availability — accept `@Disabled("requires fixture")` policy with follow-up issues (resolves OQ4)

## 2. Gradle wiring

- [ ] 2.1 Add `jvmLiveTest` source set under `kotlin.jvm()` in `krill-sdk/build.gradle.kts`, sharing classpath with `commonMain` + `jvmMain`
- [ ] 2.2 Register `Test` task `liveTest` bound to the new compilation; group = `verification`; description names live-server contract
- [ ] 2.3 Wire `useJUnitPlatform { includeTags(...) }` from a `liveTier` Gradle property (default: all three; csv splits to tag list)
- [ ] 2.4 Read `KRILL_LIVE_BASE_URL` env / `krillLiveBaseUrl` Gradle property / fallback default into a `systemProperty` the suite reads at startup
- [ ] 2.5 Confirm `liveTest` does NOT run from `check`, `build`, or `jvmTest` (Gradle dry-run + manual verification)
- [ ] 2.6 Add JUnit 5 + ktor server (test-deps) + kotlinx-coroutines-test to `jvmLiveTest` configuration in `gradle/libs.versions.toml`

## 3. Source-set guard

- [ ] 3.1 Implement a `Verify` task (or a hermetic `jvmTest` case) that scans `commonMain` / `jvmMain` / `androidMain` / `iosMain` / `wasmJsMain` for any import of the trust-all factory FQCN and fails the build if found
- [ ] 3.2 Wire the guard into `check` so it runs on every build (note: the guard itself is hermetic and does NOT touch the network)

## 4. Live HTTP client + config

- [ ] 4.1 In `jvmLiveTest`, add `LiveServerConfig` data class loaded once from system properties (URL, optional auth token, grace window for reaper)
- [ ] 4.2 Add `LiveHttpClient` factory that builds a Ktor `HttpClient` accepting the self-signed cert from the configured host (and ONLY in this source set — the guard from 3.1 enforces this)
- [ ] 4.3 Reuse the SDK's existing serialization registrations (no duplicated polymorphic config); fail loudly at startup if any KrillApp leaf type is missing a serializer
- [ ] 4.4 Implement `LiveServerProbe.awaitReachable(timeout = 60s)` returning success or printing `UNREACHABLE: <url>` and exiting non-zero

## 5. Namespace + cleanup

- [ ] 5.1 Implement `RunIdentity` (`runId = "lt-<shortUlid>"`) computed once per suite invocation
- [ ] 5.2 Implement `NodeNamer` so every test creates names of the form `<runId>/<purpose>`
- [ ] 5.3 Implement `LiveStateReaper`: at suite start, list `lt-*` nodes, delete those older than the grace window; log count reaped
- [ ] 5.4 Implement per-test `@AfterEach` cleanup for nodes that test created (track in a per-test registry)
- [ ] 5.5 Implement `@AfterAll` global sweep that deletes all nodes whose name begins with the current `runId`
- [ ] 5.6 Add a smoke test that asserts post-suite residue == 0 for the current `runId`

## 6. Smoke tier (`@Tag("smoke")`)

- [ ] 6.1 `serverReachable_returnsHealthy` — TLS handshake succeeds, server identifies itself
- [ ] 6.2 `authHandshake_succeedsWithConfiguredCreds` — auth path returns expected shape (or anonymous-ok if server unauthenticated)
- [ ] 6.3 `rootCatalogue_isParseable` — list nodes endpoint returns deserialisable response, no nodes are mutated
- [ ] 6.4 `runStartsClean` — at suite startup there are zero `lt-*` nodes for the current `runId`

## 7. Sanity tier — per-leaf-type CRUD (`@Tag("sanity")`)

- [ ] 7.1 Build a small CRUD harness `LiveCrudCase<T : KrillApp>` that templates create / read / update / read / delete + assertion shape
- [ ] 7.2 `Client` CRUD case
- [ ] 7.3 `Server.Pin` CRUD case
- [ ] 7.4 `Server.Peer` CRUD case
- [ ] 7.5 `Server.LLM` CRUD case
- [ ] 7.6 `Server.SerialDevice` CRUD case (or `@Disabled` with fixture reason)
- [ ] 7.7 `Server.Backup` CRUD case (or `@Disabled` with fixture reason)
- [ ] 7.8 `Project.Diagram` CRUD case
- [ ] 7.9 `Project.TaskList` CRUD case
- [ ] 7.10 `Project.Journal` CRUD case
- [ ] 7.11 `Project.Camera` CRUD case (or `@Disabled` with fixture reason)
- [ ] 7.12 `MQTT` CRUD case (or `@Disabled` with fixture reason)
- [ ] 7.13 `DataPoint` CRUD case
- [ ] 7.14 `DataPoint.Filter.DiscardAbove` CRUD case
- [ ] 7.15 `DataPoint.Filter.DiscardBelow` CRUD case
- [ ] 7.16 `DataPoint.Filter.Deadband` CRUD case
- [ ] 7.17 `DataPoint.Filter.Debounce` CRUD case
- [ ] 7.18 `DataPoint.Graph` CRUD case
- [ ] 7.19 `Executor.LogicGate` CRUD case
- [ ] 7.20 `Executor.OutgoingWebHook` CRUD case
- [ ] 7.21 `Executor.Lambda` CRUD case
- [ ] 7.22 One CRUD case per concrete `Trigger.*` subtype the SDK exposes (Cron, WebHook, Color, Button, …) — enumerate from `KrillApp.Trigger` children at compile time
- [ ] 7.23 Add a meta-test that walks every leaf in `KrillApp` via `krillAppChildren` and asserts a CRUD case exists (passing, failing, or `@Disabled` with reason — never silently absent)

## 8. Sanity tier — composite scenario (`@Tag("sanity")`)

- [ ] 8.1 Implement an in-process Ktor webhook sink bound to `0.0.0.0:0`, exposing a thread-safe received-list
- [ ] 8.2 Build the composite graph: DataPoint → Filter.Deadband → Trigger(threshold) → Executor.LogicGate → Executor.OutgoingWebHook(→ sink)
- [ ] 8.3 `composite_happyPath_firesExpectedWebhooks` — scripted sequence crosses threshold exactly twice, sink receives exactly two POSTs in order
- [ ] 8.4 `composite_subDeadbandChanges_areSuppressed` — sub-epsilon changes produce zero webhook hits
- [ ] 8.5 `composite_logicGateFalse_blocksFanout` — when the AND-input is false, the webhook receives nothing even past threshold

## 9. Load tier (`@Tag("load")`)

- [ ] 9.1 Implement `SnapshotProducer` — coroutine emitting at 100/sec for 60s with ±2ms jitter, recording locally-expected (timestamp, value) pairs
- [ ] 9.2 Implement read-back fetcher that polls until server snapshot count == sent count or a 30s deadline elapses
- [ ] 9.3 `loadTier_6000Snapshots_areRecordedExactly` — count, monotonic-timestamp, and value-equality assertions; on failure, report first divergent index with sent-vs-received
- [ ] 9.4 `loadTier_throughputFloor` — if server can't sustain ≥ 100/sec, report the highest sustained rate observed and fail

## 10. CI workflow

- [ ] 10.1 Add `.github/workflows/live-tests.yml` triggered on `push: branches: [main]` and `workflow_dispatch`
- [ ] 10.2 Job runs on the chosen runner label (per OQ1); checks out, sets up JDK 21, runs `./gradlew :krill-sdk:liveTest --info`
- [ ] 10.3 Inject `KRILL_LIVE_BASE_URL` (and any auth) from repo vars / secrets, falling back to default if unset
- [ ] 10.4 On failure: write a step summary listing failing test names with first-assertion message; create-or-comment an issue labelled `live-test-failure`
- [ ] 10.5 On `UNREACHABLE:` exit: label the issue `live-test-infra-down` instead, so the dev agent doesn't treat it as a code regression
- [ ] 10.6 Confirm the workflow does NOT appear in any branch protection's required-checks list (it must not block merges)

## 11. Documentation

- [ ] 11.1 Add `docs/lessons/<YYYY-MM-DD>-live-server-test-suite.md` with the three required sections (what happened, fix/structure, prevention)
- [ ] 11.2 Add a "Live test suite" section to `krill-sdk/README.md` showing how to run locally (`./gradlew :krill-sdk:liveTest -PliveTier=smoke`)
- [ ] 11.3 Update root `CLAUDE.md` Build & test → `krill-sdk` block with a `./gradlew :krill-sdk:liveTest` bullet noting it is opt-in and network-dependent

## 12. Verification

- [ ] 12.1 Run `./gradlew :krill-sdk:check` offline — must pass; confirm zero network access
- [ ] 12.2 Run `./gradlew :krill-sdk:liveTest -PliveTier=smoke` against `pi-util-01` — must pass green; confirm zero `lt-*` residue
- [ ] 12.3 Run full `./gradlew :krill-sdk:liveTest` — record wall-clock; confirm < 5 min total
- [ ] 12.4 Manually trigger the workflow on the change PR (`workflow_dispatch`) — must run on the chosen runner and report success
- [ ] 12.5 Open the PR with `Closes` link to any tracking issue, the lessons doc, and an `@qa-agent please verify` comment with reproduction steps
- [ ] 12.6 Validate change with `openspec validate add-live-server-test-suite --strict` before requesting review
