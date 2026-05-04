[![Learn More](https://img.shields.io/badge/Learn%20More-krillswarm.com-blue)](https://krillswarm.com/)
[![Download](https://img.shields.io/badge/Download-Get%20Krill-green)](https://krillswarm.com/categories/download/)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=bsautner_krill-oss&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=bsautner_krill-oss)
[![krill sdk](https://maven-badges.sml.io/sonatype-central/com.krillforge/krill-sdk/badge.svg?subject=krill-sdk)](https://maven-badges.sml.io/sonatype-central/com.krillforge/krill-sdk)
[![krill Pi4J](https://maven-badges.sml.io/sonatype-central/com.krillforge/krill-pi4j/badge.svg?subject=krill-pi4j)](https://maven-badges.sml.io/sonatype-central/com.krillforge/krill-pi4j)

---

# Krill

Krill is a privacy-first, offline-first automation platform for home automation, IoT, and process control. It runs on Raspberry Pi and any Debian-based Linux machine, connecting your devices into a secure peer-to-peer mesh — no cloud account, no subscription, no internet required. Your data never leaves your network.

Everything in Krill is a typed **Node**: data points, triggers, executors, hardware pins, projects, peers. You wire nodes together and the system reacts in real time. State changes flow over Server-Sent Events to every connected client with sub-second latency.

## What Krill Can Do

- **GPIO, PWM, and I²C** — Drive relays, solenoids, pumps, and sensors via Raspberry Pi GPIO. Hardware access runs in a dedicated [`krill-pi4j`](pi4j-ktx-service/) gRPC daemon (JDK 25, Pi4J 4.x) so the main server stays on JDK 21.
- **Cameras** — Live feeds from Raspberry Pi Camera Module 3 streamed to every client at ~2 FPS, including web/mobile, with no extra ports or firewall rules.
- **Time-Series Data Points** — Store sensor readings as `DOUBLE`, `DIGITAL`, `TEXT`, `JSON`, or the new `COLOR` type. Filter incoming values with deadband, debounce, and discard-above/below filters; visualize with built-in mini graphs.
- **Color sensing** — First-class `COLOR` data points + a Color trigger that fires when an RGB reading falls within a target range. Pairs with TCS34725-style sensors for things like pH-indicator monitoring on a CO₂ reactor.
- **Logic Gates & Calculations** — Boolean automation via AND, OR, NOT, XOR, NAND, NOR, XNOR, IMPLY, NIMPLY gates. Calculation and Compute nodes for derived values, formulas, and rolling statistics (avg/min/max/stddev).
- **Triggers** — Buttons, cron timers (second precision), high/low thresholds, color, silent-alarm watchdogs, and incoming webhooks.
- **Executors** — Outgoing webhooks, SMTP email alerts, MQTT publish, and sandboxed Python lambdas (Firejail).
- **Project Dashboards** — Per-project live views that auto-organize child nodes into Visual (camera + diagram + mini graph), Controls (digital toggles), Data, Hardware, and Automation sections. QR-code share built in.
- **Custom SVG Diagrams** — Upload SVGs in Inkscape and bind anchor regions to data points for live process-control overlays. Stock templates ship in [`cookbook/SVG Templates/`](cookbook/SVG%20Templates/).
- **Peer Mesh Networking** — Servers auto-discover each other on the LAN (UDP beacon), authenticate via a shared 4-digit PIN, and form a self-healing mesh. Nodes on any server can reference nodes on any other.
- **MQTT & Zigbee** — Pub/Sub nodes integrate with any MQTT broker and bridge to thousands of Zigbee devices via [Zigbee2MQTT](https://www.zigbee2mqtt.io/).
- **Serial Devices** — Connect Arduinos, QTPy, RP2040, Atlas Scientific probes, and other CircuitPython-friendly hardware over USB/UART.
- **Local LLM Integration** — Add a local [Ollama](https://ollama.com/) instance as an LLM Node and chat with your swarm: troubleshoot sensors, draft logic chains, generate SVG dashboards, run safety reviews — all without sending data to a cloud provider.
- **Cross-Platform Apps** — Native [Android](https://play.google.com/store/apps/details?id=krill.zone), [iOS](https://apps.apple.com/us/app/krill-zone/id6759815244), Desktop (Linux/macOS/Windows), and Web (WASM) clients built from one Kotlin Multiplatform codebase.
- **MCP for Claude** — A [Model Context Protocol](https://modelcontextprotocol.io/) server ([`krill-mcp/`](krill-mcp/)) and bundled `krill` skill let Claude Code or Claude Desktop drive your swarm directly.

## Repository Layout

This repo is the public, open half of the Krill platform. Each Gradle root has its own wrapper — there is no umbrella build.

| Path                                                | What it is                                                                                                                                              |
|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`krill-sdk/`](krill-sdk/)                          | Kotlin Multiplatform client SDK. Common, JVM, Android, iOS, and `wasmJs` targets. Published to Maven Central as `com.krillforge:krill-sdk`.             |
| [`krill-mcp/`](krill-mcp/)                          | MCP server daemon (Ktor, JVM-only) plus the bundled [`krill` Claude skill](krill-mcp/skill/krill/) that teaches Claude how to drive the MCP tools.       |
| [`pi4j-ktx-service/`](pi4j-ktx-service/)            | Pi4J client library (`krill-pi4j`, JDK 21) and the gRPC daemon (`krill-pi4j-service`, JDK 25) that owns GPIO/PWM/I²C on the Pi.                          |
| [`cookbook/lambdas/`](cookbook/lambdas/)            | Ready-to-use Python lambda examples for sensors, actuators, and CircuitPython boards.                                                                   |
| [`cookbook/SVG Templates/`](cookbook/SVG%20Templates/) | Stock SVGs for Diagram dashboards. Drop into the app or customize in [Inkscape](https://inkscape.org/).                                              |
| [`docs/lessons/`](docs/lessons/)                    | Per-fix lesson entries — every PR ships one.                                                                                                            |

## Getting Started

The fastest path is to install a server on a Raspberry Pi or Debian box, then point an app at it:

```bash
curl -fsSL https://krillswarm.com/apt/krill.gpg | sudo gpg --dearmor -o /usr/share/keyrings/krill.gpg
echo "deb [signed-by=/usr/share/keyrings/krill.gpg] https://krillswarm.com/apt stable main" | sudo tee /etc/apt/sources.list.d/krill.list
sudo apt update
sudo apt install krill

# For Raspberry Pi GPIO support:
sudo apt install krill-pi4j
```

You'll be prompted for a 4-digit cluster PIN during install — every server, app, and MCP client in your swarm shares it. The server listens on `https://<hostname>:8442`.

Apps:

- **Android** — [Google Play](https://play.google.com/store/apps/details?id=krill.zone)
- **iOS** — [App Store](https://apps.apple.com/us/app/krill-zone/id6759815244)
- **Desktop** — `sudo apt install krill-desktop` or download from [krillswarm.com](https://krillswarm.com/categories/download/)
- **Web** — open `https://<server-ip>:8442` in a browser (accept the self-signed cert)

Full walkthrough: [Getting Started](https://krillswarm.com/posts/2026/04/03/getting-started/).

## Open Source Philosophy

Krill is **free to download and use** — every app, every server, every release. The Kotlin Multiplatform platform source is synced daily to GitHub under a **source-available** license: read it, learn from it, audit the security model, fork for personal use, or build integrations on top. Redistribution and competing products are the only things off limits — that's what keeps the project sustainable.

These pieces are **fully open source** under permissive licenses with no restrictions:

- **[`krill-pi4j`](pi4j-ktx-service/)** — Standalone gRPC service for Pi GPIO/PWM/I²C. Use it from any JVM project, with or without Krill.
- **[`krill-sdk`](krill-sdk/)** — KMP client SDK on Maven Central.
- **[`krill-mcp`](krill-mcp/) + the `krill` Claude skill** — MCP server and the bundled markdown/JSON specs that drive Claude integrations.
- **[Python lambda cookbook](cookbook/lambdas/)** — Ready-to-run scripts for sensors, actuators, and CircuitPython boards.
- **[SVG dashboard templates](cookbook/SVG%20Templates/)** — Diagrams and graphics for your projects.

Pull requests welcome on any of the above. See [Our Open Source Philosophy](https://krillswarm.com/posts/2026/04/07/oss/) for the long-form version.

## Built On

| Project                                                                                | Role in Krill                                       |
|----------------------------------------------------------------------------------------|-----------------------------------------------------|
| [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)                 | Shared codebase across Android / iOS / Desktop / Web |
| [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)           | UI on every client target                           |
| [Ktor](https://ktor.io/)                                                               | Server framework on Raspberry Pi                    |
| [Pi4J](https://pi4j.com/)                                                              | GPIO / I²C / SPI access on the Pi                   |
| [H2 Database](https://h2database.com/)                                                 | Embedded time-series storage                        |
| [Eclipse Mosquitto](https://mosquitto.org/)                                            | MQTT broker integration                             |
| [Firejail](https://firejail.wordpress.com/)                                            | Sandbox for Python lambda execution                 |
| [Ollama](https://ollama.com/)                                                          | Local LLM runtime for the LLM Node                  |

## Support & Community

- [Open an issue](https://github.com/Sautner-Studio-LLC/krill-oss/issues)
- [r/krill_zone on Reddit](https://www.reddit.com/r/krill_zone/)
- [Krill website](https://krillswarm.com/)
- [Weekly stability & capability reports](https://krillswarm.com/categories/internals/)

## Privacy

Krill is offline-first by design. By default it does not collect personally identifiable information, and all data stays on your local devices. See the [Privacy Policy](https://krillswarm.com/categories/privacy-and-terms-of-service/) for details.
