## Why

Today the `krill-sdk` build only exercises unit-level behaviour against fakes. We have no continuous signal that an installed Krill server (the version the QA agent and downstream consumers actually run against) still honours the SDK's wire contract end to end. Regressions in node lifecycle, filter/trigger evaluation, or snapshot durability can ship to `main` and only surface days later in QA. We need an always-on live server plus an automated suite that talks to it from `krill-sdk` on every push to `main`, so that wire-level regressions are caught the same hour they land.

## What Changes

- Add a new `krill-sdk:liveTest` Gradle task that runs a JVM-only suite against a real, reachable Krill server (default `https://pi-util-01:8442`, overridable via `KRILL_LIVE_BASE_URL` / Gradle property).
- The suite is **excluded from `check` / `jvmTest`** so the existing fast loop stays hermetic; it only runs when explicitly invoked or from CI.
- Three test tiers, all packaged together but tagged so they can run independently:
  - **Smoke** — server reachable, auth handshake works, root catalogue lists nodes.
  - **Sanity** — for *every* `KrillApp` leaf node type (Server.Pin, Server.Peer, Server.LLM, Server.SerialDevice, Server.Backup, Project.Diagram, Project.TaskList, Project.Journal, Project.Camera, DataPoint.Filter.{DiscardAbove, DiscardBelow, Deadband, Debounce}, DataPoint.Graph, Executor.{LogicGate, OutgoingWebHook, Lambda}, Trigger.*, Client, MQTT): create → read → update → delete in isolation, then a composite scenario wiring DataPoint → Filter → Trigger → Executor end to end.
  - **Load** — drive high-frequency snapshot updates (target: 100 snapshots/sec sustained for 60s on at least one DataPoint), then read the time-series back and assert no gaps, no reorder, no value mutation.
- Each test cleans up its own nodes (idempotent setup + teardown using a per-run namespace prefix) so the live server can run forever without state bleed.
- New GitHub Actions workflow `.github/workflows/live-tests.yml` that runs `:krill-sdk:liveTest` on every push to `main` and posts a status check; failure does **not** block the push (the box is downstream of `main`) but emits an issue comment / step summary with the failing test names so the dev agent picks it up next loop.
- Lessons file under `docs/lessons/` covering the new test pattern (network-dependent suite, namespace cleanup, severity expectations).

## Capabilities

### New Capabilities
- `live-server-test-suite`: a JVM-only test module inside `krill-sdk` that runs three tiers (smoke / sanity / load) against a configurable live Krill server, exercises every `KrillApp` node type independently and as a composite, verifies filter + trigger evaluation on injected data, and validates high-frequency snapshot durability — invoked locally on demand and from CI on every push to `main`.

### Modified Capabilities
<!-- None — this introduces a brand-new capability; existing SDK specs are unaffected. -->

## Impact

- **Code**: new source set `krill-sdk/src/jvmLiveTest/kotlin/...` (or a tagged subset of `commonTest` — chosen in design); new Gradle task and configuration in `krill-sdk/build.gradle.kts`; a small reusable HTTP client wrapper that points at the live server.
- **CI**: new GitHub Actions workflow file under `.github/workflows/`; secret/var for `KRILL_LIVE_BASE_URL` (defaulted) and credentials if the live server enforces auth.
- **Infra dependency**: the suite assumes `https://pi-util-01:8442` is reachable and self-signed-cert-tolerant from the GH Actions runner. Where that runner lives (cloud vs self-hosted) is a design decision flagged in `design.md`.
- **Build time**: live test wall-clock is ~2–5 minutes (load tier dominates). Hermetic `check` is unaffected because `liveTest` is excluded.
- **Docs**: `krill-sdk/README.md` gets a "Live test suite" section; `CLAUDE.md` build & test block gets a new bullet pointing at `./gradlew liveTest`.
- **No changes** to published artifacts, public SDK API, or the `krill-mcp` / `krill-pi4j` modules.
