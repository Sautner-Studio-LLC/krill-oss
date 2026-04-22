# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This file is scoped to `krill-mcp/`. The parent `krill-oss/CLAUDE.md` describes
the sibling projects and the PIN-derivation contract shared across the swarm —
read it first for cross-project context.

## Build & run

This is a standalone multi-module Gradle build with its own wrapper. Run everything from `krill-mcp/`.

```bash
./gradlew :krill-mcp-service:shadowJar                     # Fat JAR → krill-mcp-service/build/libs/krill-mcp-all.jar
./gradlew :krill-mcp-service:build                         # Build (depends on shadowJar) + tests
./gradlew :krill-mcp-service:test --tests "FQCN.method"    # Single test
./gradlew :krill-mcp-service:run                           # Not wired — run the fat JAR directly:
java -jar krill-mcp-service/build/libs/krill-mcp-all.jar   # Needs /etc/krill-mcp/config.json + credentials
```

JVM toolchain is pinned to JDK 21 on the service module (foojay auto-provisions). The daemon expects:
- `/etc/krill-mcp/config.json` — `KrillMcpConfig` (listenPort, pinDerivedKeyPath, `seeds[]`).
- `/etc/krill-mcp/credentials/pin_derived_key` — 64-char hex HMAC of the cluster PIN.

Without a PIN-derived key the server boots but rejects every `/mcp` request as `401`. `/healthz` stays unauthenticated.

## Architecture

The daemon is a single JVM process: **Ktor Netty server** exposes `POST /mcp` (JSON-RPC 2.0, MCP Streamable HTTP) and fans out to one or more **Krill servers** over HTTPS.

Data flow:
```
Claude ──Bearer──▶ KtorApp /mcp ──▶ McpServer ──▶ Tool.execute() ──▶ KrillRegistry.resolve() ──▶ KrillClient ──HTTPS──▶ Krill server
```

Key modules (all under `krill-mcp-service/src/main/kotlin/krill/zone/mcp/`):

- **`Main.kt`** — wires everything. The `tools = listOf(...)` block is the authoritative tool registry; adding a new MCP tool means adding a `Tool` impl and listing it here.
- **`http/KtorApp.kt`** — Ktor bootstrap. Bearer check in `authorized()`; everything else is plain JSON-RPC pass-through. Notifications (no `id`) respond `202 Accepted` with no body, per Streamable HTTP.
- **`mcp/McpServer.kt`** — JSON-RPC dispatcher. Implements only the subset Claude clients need: `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`. `PROTOCOL_VERSION = "2025-06-18"`. No SSE streaming, no resources, no prompts.
- **`mcp/Tool.kt`** + **`mcp/tools/*Tool.kt`** — each tool declares `name`, `description`, a JSON Schema `inputSchema`, and a `suspend execute()` returning any `JsonElement`. The dispatcher wraps the return as a single `text` content block (stringified JSON); errors become `isError: true` with `ERROR: <message>`.
- **`krill/KrillRegistry.kt`** — owns one `KrillClient` per seed in `config.seeds[]`. `bootstrap()` probes each seed with 5 retries / 2 s backoff to handle the **systemd startup race** where `krill-mcp` comes up before the Krill server it targets is listening. `resolve()` does a synchronous single-shot re-probe if the registry is still empty on a call — this is what makes the `reseed_servers` MCP tool viable without shell access. Seed lookup accepts server id, host, or `host:port`.
- **`krill/KrillClient.kt`** — thin HTTPS client per server. Trusts self-signed certs (Krill installs its own); security comes from the PIN-derived bearer, not the cert. Exposes `/health`, `/nodes`, `/node/{id}`, `/node/{id}/data/series`, plus Project/Diagram writes. Returns raw `JsonElement` trees — deliberately *does not* mirror Krill's internal data model.
- **`auth/PinDerivation.kt`** — `HMAC-SHA256(key="krill-api-pbkdf2-v1", data=PIN)` → hex. **Must stay byte-identical** with the `openssl dgst` line in `package/DEBIAN/postinst` and the upstream Krill server's postinst. Any drift breaks authentication swarm-wide. `constantTimeEquals` is used for bearer validation.
- **`auth/PinProvider.kt`** — reads `pin_derived_key` from disk; lazy and re-readable.
- **`krill/KrillClient.kt` → `publicBaseUrl`** — derived at probe time from `/health.meta` (name + port, with `.local` suffixing for bare/mDNS names). Used when building `Diagram.source` URLs so phones/browsers on the LAN can actually fetch the SVG — a `localhost:8442` seed would be unreachable from any other host.

## Adding an MCP tool

1. Implement `Tool` in `mcp/tools/` (pattern: `*Tool.kt` in either `KrillTools.kt` for reads or `DiagramTools.kt` for Project/Diagram writes — both are multi-class files).
2. Register it in `Main.kt`'s `tools = listOf(...)`.
3. Update **both** companion skill references — `skill/krill/SKILL.md` and `skill/krill/references/mcp-tools.md` — or the skill documentation drifts from reality.
4. Tool return values are stringified verbatim into a text content block; return a `JsonObject` / `JsonArray` so Claude gets structured data, not a string.

## Version sync — five sites

Every release bumps version in five places; drift causes misleading headers, wrong skill behavior, or an `apt install` that doesn't overwrite. Full list:

1. `krill-mcp-service/build.gradle.kts` → `version = "X.Y.Z"`
2. `krill-mcp-service/src/main/kotlin/krill/zone/mcp/Main.kt` → `SERVER_VERSION = "X.Y.Z"`
3. `krill-mcp-service/package/DEBIAN/control` → `Version: X.Y.Z`
4. `skill/krill/SKILL.md` → frontmatter `version: X.Y.Z`
5. Every `vX.Y.Z` literal in `skill/krill/SKILL.md` and `skill/krill/references/mcp-tools.md` (`grep -rn 'v0\.0\.' skill/` catches them)

The `PinDerivation` HMAC key (`"krill-api-pbkdf2-v1"`) is **not** part of this versioning — it's a wire contract and must never change.

## Distribution

The daemon ships as a Debian package (`package/` is a staged `dpkg-deb` tree):

- `postinst` creates the `krill` system user, either symlinks `/etc/krill-mcp/credentials/pin_derived_key` to an existing Krill install's key **or** prompts interactively for a 4-digit PIN, writes `/etc/systemd/system/krill-mcp.service`, and prints the bearer token + Custom Connector URL.
- `Architecture: all` — pure JVM, no native code.
- The `.deb` itself is built by the **private** `krill` repo's "Deploy Debian Repo" workflow. See `DEPLOYMENT.md` for the exact workflow snippet to insert after the `krill-pi4j` build step.

## Companion skill (`skill/krill/`)

Documentation-only Claude skill that teaches Claude how to drive the MCP tools and author SVG dashboards. It bundles 37 Krill node-type JSON specs copied from the closed-source `krill` repo's `shared/src/commonMain/resources/`. Link it into a local Claude install with:

```bash
ln -s "$(pwd)/skill/krill" ~/.claude/skills/krill
```

When touching MCP tool shapes or adding/removing tools, update `skill/krill/references/mcp-tools.md` in the same change.
