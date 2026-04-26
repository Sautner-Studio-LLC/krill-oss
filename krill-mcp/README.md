# krill-mcp

Install Krill MCP alongside your Krill servers to connect them to Claude Desktop and Claude Code via a Model Context Protocol server. This allows you to use LLMs to discover, monitor, and interact with your Krill swarm — including creating live dashboards, writing code to control your project, and even asking for improvement suggestions on your diagrams and automations.




A [Model Context Protocol](https://modelcontextprotocol.io) server that exposes a [Krill](https://krillswarm.com) swarm to Claude. Install once on any Debian box on your LAN — Claude Desktop and Claude Code connect to it as a remote Custom Connector, and it fans out to every Krill server in your swarm using the same 4-digit PIN you use for the Krill apps.

```
┌──────────────────────┐                ┌────────────────────────┐
│  Claude Desktop/Code │                │   Krill server (Pi)    │
│  (Custom Connector)  │───HTTPS MCP────▶│                        │
└──────────────────────┘   :50052/mcp   │   krill-mcp daemon     │─┐
                                        └──┬─────────────────────┘ │
                                           │ HTTPS /nodes, /node/* │
                                           ▼                       │
                                        ┌────────────────────────┐ │
                                        │   Krill server peers   │◀┘
                                        │   (auto-discovered)    │
                                        └────────────────────────┘
```

## How PIN auth works

`krill-mcp` uses the same PIN-derived bearer token as the rest of Krill:

```
pin_derived_key = HMAC-SHA256(key="krill-api-pbkdf2-v1", data=PIN)   // hex
```

- If installed **alongside** a krill server, it reads `/etc/krill/credentials/pin_derived_key` directly — no prompt.
- Otherwise, `postinst` prompts for the same 4-digit PIN and derives the token locally. The PIN never goes over the wire.

Claude clients authenticate *to* the MCP server using the same token. `postinst` prints the bearer to paste into your Custom Connector Authorization header.

## Building from source

```bash
./gradlew :krill-mcp-service:shadowJar
# → krill-mcp-service/build/libs/krill-mcp-all.jar
```

## MCP tool surface

### Read (discovery + inspection)

| Tool             | Description                                                      |
|------------------|------------------------------------------------------------------|
| `list_servers`   | All Krill servers the MCP daemon knows about                     |
| `list_nodes`     | Nodes on a given server, optionally filtered by type             |
| `get_node`       | Single node with its current meta/state                          |
| `read_series`    | Time-series data for a DataPoint node over a time range          |
| `server_health`  | Health check for a given Krill server                            |

### Write (Projects + Diagrams)

| Tool                      | Description                                                                      |
|---------------------------|----------------------------------------------------------------------------------|
| `list_projects`           | List `KrillApp.Project` containers on a server                                   |
| `create_project`          | Create a new Project node — parent for diagrams, task lists, journals, cameras   |
| `create_diagram`          | Upload an SVG file + create a `KrillApp.Project.Diagram` node pointing at it     |
| `update_diagram`          | Re-upload the SVG and/or rewrite bindings — for "improve this diagram" flows     |
| `get_diagram`             | Fetch a Diagram's node metadata AND the SVG content behind its `source` URL     |
| `upload_diagram_file`     | Raw SVG PUT to `/project/{id}/diagram/{file}` static path (no node)              |
| `download_diagram_file`   | Raw SVG GET from the same static path                                            |

### Write (any node type, + DataPoint values, + cascade delete) — v0.0.8

| Tool                | Description                                                                       |
|---------------------|-----------------------------------------------------------------------------------|
| `list_node_types`   | Registry of every `KrillApp.*` type `create_node` can build — default meta, valid parents/children, side-effect level, per-field shape hints |
| `create_node`       | Create a node of any registered type; overlay `meta` fields over the type default. Response includes a deterministic `parentValidation` object. |
| `record_snapshot`   | Record one or many `{timestamp, value}` pairs onto a DataPoint (validated against its `dataType`) |
| `delete_node`       | Delete a node by id. Cascades on the server — deleting a Project tears down every Diagram/TaskList/Journal/Camera beneath it in one call. Requires `confirm: true`. |

In-place meta edits for non-Diagram types remain outside this MCP's surface — for those, fetch with `get_node`, mutate, re-POST via `create_node`'s underlying route (or use the JSON-RPC fallback to `/node/{id}` directly).

## Companion Claude skill

`skill/krill/` is a Claude skill that teaches Claude how to discover and reason about a Krill swarm via the MCP tools above, and to author SVG dashboards bound to live `DataPoint` state. To install for local use, symlink it into your Claude skills dir:

```bash
ln -s "$(pwd)/skill/krill" ~/.claude/skills/krill
```

The skill bundles all 37 Krill node-type LLM specs (copied from the closed `krill` repo's `shared/src/commonMain/resources/`) plus the `k_*` SVG anchor convention used by `KrillApp.Project.Diagram`.

## Version sync

Every release bumps version in **five** sites — drift breaks something every time. Checklist:

1. `krill-mcp-service/build.gradle.kts` → `version = "X.Y.Z"`
2. `krill-mcp-service/src/main/kotlin/krill/zone/mcp/Main.kt` → `SERVER_VERSION = "X.Y.Z"`
3. `krill-mcp-service/package/DEBIAN/control` → `Version: X.Y.Z`
4. `skill/krill/SKILL.md` → frontmatter `version: X.Y.Z`
5. Every `vX.Y.Z` literal in `skill/krill/SKILL.md` and `skill/krill/references/mcp-tools.md` (grep `vX\.Y\.Z` to find them)

The PIN-derivation HMAC contract in `auth/PinDerivation.kt` is independent of this version and must stay byte-identical with the `openssl dgst` line in both the `krill-mcp` and the upstream `krill` `postinst` scripts.

## License

Apache License 2.0.
