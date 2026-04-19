# krill-mcp

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

## MCP tool surface (v1)

| Tool             | Description                                                      |
|------------------|------------------------------------------------------------------|
| `list_servers`   | All Krill servers the MCP daemon knows about                     |
| `list_nodes`     | Nodes on a given server, optionally filtered by type             |
| `get_node`       | Single node with its current meta/state                          |
| `read_series`    | Time-series data for a DataPoint node over a time range          |
| `server_health`  | Health check for a given Krill server                            |

## License

Apache License 2.0.
