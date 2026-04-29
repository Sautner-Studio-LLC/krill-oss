## ADDED Requirements

### Requirement: Dedicated Gradle task for live tests

The `krill-sdk` module SHALL expose a Gradle task named `liveTest` that compiles and runs the live-server suite. The task MUST NOT be reachable from `check`, `build`, `jvmTest`, or any other default-path task. Invoking `./gradlew :krill-sdk:check` SHALL NOT make any network call to a Krill server.

#### Scenario: Default check is hermetic
- **WHEN** a contributor runs `./gradlew :krill-sdk:check` with no network access
- **THEN** the build succeeds and no live-test class is compiled or executed

#### Scenario: liveTest task exists
- **WHEN** a contributor runs `./gradlew :krill-sdk:tasks --all`
- **THEN** the task list includes `liveTest` under the `verification` group with a description that names the live-server contract

### Requirement: Server URL is externally configurable with a sane default

The suite SHALL resolve the live server base URL in this order: (1) env var `KRILL_LIVE_BASE_URL`, (2) Gradle property `krillLiveBaseUrl`, (3) the default `https://pi-util-01:8442`. Resolution happens once at suite startup and is logged before any test runs.

#### Scenario: Env var overrides default
- **WHEN** `KRILL_LIVE_BASE_URL=https://other.host:9000` is set and `liveTest` runs
- **THEN** the suite logs `live server: https://other.host:9000` and every HTTP request targets that host

#### Scenario: Gradle property overrides default but loses to env var
- **WHEN** `liveTest` runs with both `KRILL_LIVE_BASE_URL=https://from-env` and `-PkrillLiveBaseUrl=https://from-prop`
- **THEN** the suite uses `https://from-env` and logs the source as `env`

#### Scenario: Default is used when nothing is set
- **WHEN** `liveTest` runs with no overrides
- **THEN** the suite uses `https://pi-util-01:8442`

### Requirement: Suite fails fast on unreachable server

If the configured base URL is unreachable after a bounded reachability check (≤ 60s of retries), the suite SHALL fail immediately with an exit message that begins with the literal token `UNREACHABLE:` followed by the URL. The suite MUST NOT execute any test method in that state.

#### Scenario: Unreachable host short-circuits the suite
- **WHEN** `liveTest` runs against a base URL that refuses connections for the full reachability window
- **THEN** the build fails, no test method is executed, and stderr contains `UNREACHABLE: <url>`

### Requirement: Three test tiers selectable by tag

The suite SHALL group tests under exactly three JUnit 5 tags: `smoke`, `sanity`, `load`. Every test class or method in the suite MUST carry exactly one of these tags. Invoking `liveTest` with no tier filter runs all three; passing `-PliveTier=<csv>` runs only the listed tiers.

#### Scenario: Single-tier run
- **WHEN** a contributor runs `./gradlew :krill-sdk:liveTest -PliveTier=smoke`
- **THEN** only methods tagged `smoke` execute and the build reports the tier in its summary

#### Scenario: Multi-tier run
- **WHEN** `liveTest -PliveTier=smoke,sanity` is invoked
- **THEN** smoke and sanity tests run, load tests are skipped, and the load skip count is reported

#### Scenario: Every test has exactly one tier tag
- **WHEN** the build runs a meta-check over the suite source
- **THEN** every `@Test` method carries exactly one of `@Tag("smoke")`, `@Tag("sanity")`, `@Tag("load")` and the meta-check passes

### Requirement: Smoke tier verifies basic reachability and handshake

The `smoke` tier SHALL contain at least these assertions: server reachable on TLS, auth handshake succeeds with the configured credentials (or anonymous if the server is unauthenticated), and the root catalogue endpoint returns a parseable response. Smoke MUST NOT create or mutate any node.

#### Scenario: Smoke succeeds against a healthy server
- **WHEN** the smoke tier runs against a reachable, healthy `pi-util-01`
- **THEN** all smoke tests pass and zero `lt-*` nodes exist on the server after the tier

#### Scenario: Smoke detects auth failure
- **WHEN** the configured credentials are rejected by the server
- **THEN** the smoke handshake test fails with a message naming the auth scheme attempted

### Requirement: Sanity tier covers every leaf KrillApp node type in isolation

The `sanity` tier SHALL include one CRUD test per leaf node type defined in `KrillApp` — at minimum: `Client`, `Server.Pin`, `Server.Peer`, `Server.LLM`, `Server.SerialDevice`, `Server.Backup`, `Project.Diagram`, `Project.TaskList`, `Project.Journal`, `Project.Camera`, `MQTT`, `DataPoint`, `DataPoint.Filter.DiscardAbove`, `DataPoint.Filter.DiscardBelow`, `DataPoint.Filter.Deadband`, `DataPoint.Filter.Debounce`, `DataPoint.Graph`, `Executor.LogicGate`, `Executor.OutgoingWebHook`, `Executor.Lambda`, and every concrete `Trigger` subtype the SDK exposes. Each test MUST create the node, read it back, update at least one field, read again to confirm the update, and delete it — asserting expected response shape at every step. Tests for node types that genuinely cannot be exercised on the live server (e.g. require an external camera fixture) SHALL be marked `@Disabled` with a reason that names the missing fixture and links to a follow-up issue.

#### Scenario: Sanity covers every required leaf type
- **WHEN** the sanity tier runs
- **THEN** the test report contains exactly one CRUD case per leaf node type listed above (passing, failing, or `@Disabled` with reason — never silently absent)

#### Scenario: A CRUD case asserts every step
- **WHEN** the sanity test for `DataPoint.Filter.Deadband` runs against a healthy server
- **THEN** create → read → update → read → delete each return success and the read-back values match what was sent

### Requirement: Composite scenario exercises filter, trigger, and executor end-to-end

The sanity tier SHALL include at least one composite scenario that wires `DataPoint → DataPoint.Filter.Deadband → Trigger (threshold) → Executor.LogicGate → Executor.OutgoingWebHook` and asserts that an injected snapshot sequence produces the expected fan-out at the webhook sink. The webhook sink MUST be an in-process Ktor server bound to an ephemeral port, not an external service.

#### Scenario: Composite happy path
- **WHEN** the composite scenario ingests a scripted sequence designed to cross the trigger threshold exactly twice
- **THEN** the in-process webhook sink receives exactly two POSTs in the expected order with the expected payloads, and no extras

#### Scenario: Filter suppresses sub-deadband changes
- **WHEN** the composite scenario ingests changes within the configured deadband epsilon
- **THEN** the webhook sink receives zero POSTs from those samples

### Requirement: Load tier validates high-frequency snapshot durability

The `load` tier SHALL run at least one test that ingests snapshots into a freshly created `DataPoint` at a sustained target rate of at least 100 snapshots/second for at least 60 seconds, then reads the full series back from the server and asserts (a) snapshot count exactly equals the count sent, (b) timestamps are strictly monotonically increasing, (c) values match the locally-recorded expected sequence index-for-index. If the server cannot sustain the target ingest rate, the test SHALL fail with a message that reports the highest sustained rate observed.

#### Scenario: Load passes on a healthy server
- **WHEN** the load test ingests 6,000 snapshots at 100/sec into a single DataPoint and reads them back
- **THEN** the count, ordering, and value-equality assertions all hold

#### Scenario: Drop or reorder is caught
- **WHEN** the read-back series differs from the sent series in count, order, or any value
- **THEN** the test fails with a message that names the first divergent index and the sent vs. received values at that index

### Requirement: All test-created state is namespaced and torn down

Every node, project, or other server-side artefact the suite creates SHALL be named with a per-run prefix `lt-<runId>` where `<runId>` is unique to the suite invocation. After each test (success or failure) and again at suite end, the suite MUST delete all artefacts it created. A startup hook MUST also delete any `lt-*` artefacts older than a configurable grace window (default: 1 hour) to reap leftovers from crashed prior runs.

#### Scenario: Successful run leaves no residue
- **WHEN** the suite runs to completion against a healthy server
- **THEN** the count of `lt-*` artefacts on the server before and after the run is zero

#### Scenario: Crashed prior run is reaped
- **WHEN** the suite starts and finds `lt-*` artefacts from a run > 1 hour old
- **THEN** the startup hook deletes them and logs the count reaped before any test method runs

#### Scenario: Failure path still cleans up
- **WHEN** a sanity test method fails mid-CRUD
- **THEN** the per-test teardown deletes any artefacts that test created, and the suite-end hook confirms zero residue

### Requirement: Self-signed TLS trust is scoped to the live source set

The trust-all (or pinned-self-signed) HTTP client factory used to talk to `pi-util-01` SHALL exist only inside the `jvmLiveTest` source set. No file in `commonMain`, `jvmMain`, `androidMain`, `iosMain`, or `wasmJsMain` may import or reference that factory. The build MUST include a guard (a test or a `Verify` task) that fails if such an import is introduced.

#### Scenario: Guard catches a leak into shipping code
- **WHEN** a contributor moves the trust-all factory into `jvmMain` and runs `./gradlew :krill-sdk:check`
- **THEN** the guard fails with a message that names the leaking file and forbidden symbol

### Requirement: CI workflow runs the suite on every push to main

The repository SHALL include a GitHub Actions workflow that runs `:krill-sdk:liveTest` on every push to the `main` branch. The workflow MUST NOT block merges (it does not gate any required check). On failure it SHALL post a step summary listing failing test names and either open a new issue with label `live-test-failure` or comment on the existing open one for the same failure signature.

#### Scenario: Green run on push
- **WHEN** a commit lands on `main` and the live server is healthy
- **THEN** the workflow runs `:krill-sdk:liveTest`, reports success on the check, and posts no issue

#### Scenario: Red run files an issue
- **WHEN** a commit lands on `main` and at least one live test fails
- **THEN** the workflow's step summary lists every failing test with its first-failure assertion message, and an issue labelled `live-test-failure` either is created or receives a new comment with the run URL

#### Scenario: Infra-down is distinguished from real failure
- **WHEN** the suite exits with `UNREACHABLE:` (server down)
- **THEN** the workflow labels its issue `live-test-infra-down` rather than `live-test-failure`, signalling the dev agent not to treat it as a regression

### Requirement: Lessons doc accompanies the change

The PR introducing the suite SHALL add a `docs/lessons/<YYYY-MM-DD>-live-server-test-suite.md` entry covering: what happened (why we needed live coverage), how the suite is structured (tiers, namespace prefix, self-signed trust), and prevention rules (never make `check` network-dependent; never let trust-all leak into shipping source sets).

#### Scenario: Lesson file present
- **WHEN** the PR is opened
- **THEN** `docs/lessons/` contains a new dated entry whose body has the three required sections
