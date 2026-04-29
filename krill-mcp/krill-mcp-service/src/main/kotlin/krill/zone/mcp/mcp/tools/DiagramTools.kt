package krill.zone.mcp.mcp.tools

import krill.zone.mcp.krill.KrillClient
import krill.zone.mcp.krill.KrillRegistry
import krill.zone.mcp.mcp.Tool
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Type discriminators used by the Krill server's kotlinx.serialization config.
 *
 * The server's sealed-class polymorphism has no `@SerialName` overrides, so the
 * discriminator value is the fully qualified class name. If the Krill shared
 * module is ever repackaged, these strings must move with it — and also the
 * corresponding constant on every existing persisted node would need a data
 * migration on the server side.
 */
private const val TYPE_PROJECT = "krill.zone.shared.KrillApp.Project"
private const val TYPE_DIAGRAM = "krill.zone.shared.KrillApp.Project.Diagram"
private const val META_PROJECT = "krill.zone.shared.krillapp.project.ProjectMetaData"
private const val META_DIAGRAM = "krill.zone.shared.krillapp.project.diagram.DiagramMetaData"

/**
 * Authoring contract the agent has to follow for anchors to actually bind.
 * Embedded in every create/update tool description so cold LLM sessions don't
 * have to infer it from elsewhere.
 */
private const val ANCHOR_CONTRACT =
    "Anchor contract: each anchor is a bare <rect id=\"k_<node-uuid>\" fill=\"none\"/> with NO child elements, " +
        "no <text>, no strokes — the Krill client overlays the live UI inside that rect at runtime. " +
        "anchorBindings maps anchor_id → node_uuid (same uuid as inside the id), e.g. " +
        "{\"k_1c9dce76-ba65-48b5-b842-32ad97a96f80\": \"1c9dce76-ba65-48b5-b842-32ad97a96f80\"}."

/**
 * Create a new `KrillApp.Project` node. Projects sit under the server node and
 * act as organizational containers for diagrams, task lists, journals, cameras.
 */
class CreateProjectTool(private val registry: KrillRegistry) : Tool {
    override val name = "create_project"
    override val description =
        "Create a new Project node (container for Diagrams, TaskLists, Journals, Cameras) on a Krill server."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") {
                put("type", "string")
                put("description", "Krill server id, host, or host:port. Defaults to the first registered server.")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Human-readable name for the project.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional project description.")
            }
        }
        putJsonArray("required") { add("name") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val name = arguments["name"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: name")
        val description = arguments["description"]?.jsonPrimitive?.contentOrNull ?: ""

        val newId = UUID.randomUUID().toString()
        val node = buildJsonObject {
            put("id", newId)
            put("parent", client.serverId)
            put("host", client.serverId)
            putJsonObject("type") { put("type", TYPE_PROJECT) }
            put("state", "CREATED")
            putJsonObject("meta") {
                put("type", META_PROJECT)
                put("name", name)
                put("description", description)
                put("error", "")
            }
            put("timestamp", 0L)
        }
        client.postNode(node)

        return buildJsonObject {
            put("server", client.serverId)
            put("projectId", newId)
            put("name", name)
            put("message", "Project created. Use projectId as the parent when creating child Diagram / TaskList / Journal / Camera nodes.")
        }
    }
}

/** List existing Project nodes on a server. */
class ListProjectsTool(private val registry: KrillRegistry) : Tool {
    override val name = "list_projects"
    override val description = "List all Project nodes on a Krill server (id, name, description)."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val projects = client.nodes().mapNotNull { raw ->
            val obj = raw as? JsonObject ?: return@mapNotNull null
            val typeName = obj["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
            if (typeName != TYPE_PROJECT) return@mapNotNull null
            val meta = obj["meta"] as? JsonObject
            buildJsonObject {
                put("id", obj["id"] ?: JsonNull)
                put("name", meta?.get("name") ?: JsonNull)
                put("description", meta?.get("description") ?: JsonNull)
            }
        }
        return buildJsonObject {
            put("server", client.serverId)
            put("count", projects.size)
            put("projects", JsonArray(projects))
        }
    }
}

/**
 * Create a new `KrillApp.Project.Diagram` node.
 *
 * Krill stores the SVG as a file, not inline: `DiagramMetaData.source` is a URL
 * that client apps fetch to render the dashboard. This tool does the full
 * round-trip:
 *
 *   1. Pick a filename: [uploadFileName] if given, else slugify(name)+".svg".
 *   2. PUT the SVG bytes to `/project/{projectId}/diagram/{fileName}` — the
 *      server writes the file with the right owner and permissions.
 *   3. POST the Diagram node with `meta.source` = the public URL of that file.
 *
 * Upload always happens. `uploadFileName` is an override, not a gate.
 */
class CreateDiagramTool(private val registry: KrillRegistry) : Tool {
    override val name = "create_diagram"
    override val description =
        "Create a Diagram node end-to-end: always uploads the SVG to /project/{projectId}/diagram/{fileName} " +
            "(filename defaults to slug(name)+.svg unless `uploadFileName` is given), then posts a KrillApp.Project.Diagram " +
            "node whose `meta.source` is the resulting public URL. $ANCHOR_CONTRACT"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("projectId") {
                put("type", "string")
                put("description", "Id of the parent KrillApp.Project node. Use list_projects or create_project to obtain.")
            }
            putJsonObject("name") {
                put("type", "string")
                put("description", "Display name. Also drives the default filename (lowercase_snake_case + .svg) unless `uploadFileName` is given.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional description.")
            }
            putJsonObject("source") {
                put("type", "string")
                put("description", "SVG content (the bytes, not a URL). The server stores this as a file at /project/{projectId}/diagram/{fileName} and sets meta.source to the resulting URL. Must contain a <svg> tag. 2 MB cap.")
            }
            putJsonObject("uploadFileName") {
                put("type", "string")
                put("description", "Optional filename override. Must match ^[a-zA-Z0-9_.-]+$ and end with .svg. Defaults to slug(name)+.svg. Upload happens either way — this only controls where.")
            }
            putJsonObject("anchorBindings") {
                put("type", "object")
                put("description", "Map from SVG anchor id (e.g. 'k_<node-uuid>') to the target node UUID. Direction is anchor_id → node_uuid. For the anchor_id=k_<uuid> convention, both sides are the same uuid.")
                putJsonObject("additionalProperties") { put("type", "string") }
            }
        }
        putJsonArray("required") {
            add("projectId")
            add("name")
            add("source")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val projectId = arguments["projectId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: projectId")
        val name = arguments["name"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: name")
        val description = arguments["description"]?.jsonPrimitive?.contentOrNull ?: ""
        val svg = arguments["source"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: source (SVG content, not a URL).")
        val bindings = arguments["anchorBindings"] as? JsonObject ?: JsonObject(emptyMap())
        val fileName = arguments["uploadFileName"]?.jsonPrimitive?.contentOrNull?.also { validateSvgFileName(it) }
            ?: defaultFileName(name)

        if (!svg.contains("<svg", ignoreCase = true)) {
            error("source does not look like SVG (missing <svg> tag). Pass the SVG markup as the `source` argument, not a URL.")
        }

        // 1. Upload the file — server writes to /srv/krill/project/{projectId}/diagram/{fileName}
        client.uploadDiagramFile(projectId, fileName, svg)

        // 2. Build the public URL other Krill clients will load to render this diagram.
        val sourceUrl = "${client.publicBaseUrl.trimEnd('/')}/project/$projectId/diagram/$fileName"

        // 3. Post the node.
        val newId = UUID.randomUUID().toString()
        val node = buildJsonObject {
            put("id", newId)
            put("parent", projectId)
            put("host", client.serverId)
            putJsonObject("type") { put("type", TYPE_DIAGRAM) }
            put("state", "CREATED")
            putJsonObject("meta") {
                put("type", META_DIAGRAM)
                put("name", name)
                put("description", description)
                put("source", sourceUrl)
                put("anchorBindings", bindings)
                put("error", "")
            }
            put("timestamp", 0L)
        }
        client.postNode(node)

        return buildJsonObject {
            put("server", client.serverId)
            put("projectId", projectId)
            put("diagramId", newId)
            put("name", name)
            put("fileName", fileName)
            put("source", sourceUrl)
            put("anchorCount", bindings.size)
            put("anchorBindings", bindings)
            put("sourceBytes", svg.toByteArray(Charsets.UTF_8).size)
        }
    }
}

/**
 * Update an existing Diagram node.
 *
 * Implementation note: starts from the existing `meta` JsonObject and mutates
 * fields in place, rather than rebuilding from scratch. This preserves the
 * polymorphic `type` discriminator the server emits as well as any fields this
 * MCP doesn't know about, so the server's kotlinx.serialization-based Node
 * deserializer never sees a shape mismatch. (Rebuilding with a hard-coded
 * discriminator caused a silent no-op in v0.0.5 — the server accepted the body
 * but dropped the anchorBindings update.)
 *
 * Upload semantics: when `source` is provided, the file is always re-uploaded.
 * Filename priority: [uploadFileName] → existing source URL's filename → slug
 * of the (new or existing) name. Passing `source` alone with no `uploadFileName`
 * re-uploads to the same path the current URL already references, which is
 * what callers expect 99% of the time.
 */
class UpdateDiagramTool(private val registry: KrillRegistry) : Tool {
    override val name = "update_diagram"
    override val description =
        "Update a Diagram node. When `source` is given, the SVG file is always re-uploaded — by default to the same path the current meta.source URL references, so the URL stays stable. " +
            "Pass `uploadFileName` only to rename the file. Any of name/description/source/anchorBindings can be updated independently; omitted fields keep their current value. $ANCHOR_CONTRACT"
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("diagramId") {
                put("type", "string")
                put("description", "Id of the existing KrillApp.Project.Diagram node to update.")
            }
            putJsonObject("name") { put("type", "string") }
            putJsonObject("description") { put("type", "string") }
            putJsonObject("source") {
                put("type", "string")
                put("description", "Replacement SVG content (the bytes, not a URL). When given, the file is re-uploaded; omit to leave the file untouched.")
            }
            putJsonObject("uploadFileName") {
                put("type", "string")
                put("description", "Optional rename. Defaults to the filename already referenced by the current meta.source URL (stable URL). Required pattern: ^[a-zA-Z0-9_.-]+$ ending in .svg.")
            }
            putJsonObject("anchorBindings") {
                put("type", "object")
                put("description", "Replacement anchor → node-id map (entire map is replaced, not merged). Omit to keep the current bindings. Direction: anchor_id → node_uuid.")
                putJsonObject("additionalProperties") { put("type", "string") }
            }
        }
        putJsonArray("required") { add("diagramId") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val diagramId = arguments["diagramId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: diagramId")

        val existing = client.node(diagramId) as? JsonObject
            ?: error("Diagram $diagramId not found on server ${client.serverId}.")
        val existingType = existing["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (existingType != TYPE_DIAGRAM) {
            error("Node $diagramId is not a Diagram (got $existingType).")
        }
        val existingMeta = existing["meta"] as? JsonObject
            ?: error("Node $diagramId has no meta object.")
        val projectId = existing["parent"]?.jsonPrimitive?.contentOrNull
            ?: error("Existing diagram has no parent project id.")

        val newName = arguments["name"]?.jsonPrimitive?.contentOrNull
        val newDescription = arguments["description"]?.jsonPrimitive?.contentOrNull
        val newSvg = arguments["source"]?.jsonPrimitive?.contentOrNull
        val newFileName = arguments["uploadFileName"]?.jsonPrimitive?.contentOrNull?.also { validateSvgFileName(it) }
        val newBindings = arguments["anchorBindings"] as? JsonObject

        if (newSvg != null && !newSvg.contains("<svg", ignoreCase = true)) {
            error("source does not look like SVG (missing <svg> tag). Pass the SVG markup, not a URL.")
        }

        val effectiveName = newName ?: existingMeta["name"]?.jsonPrimitive?.contentOrNull ?: "Diagram"
        val existingSourceUrl = existingMeta["source"]?.jsonPrimitive?.contentOrNull ?: ""
        val existingFileName = existingSourceUrl.substringAfterLast('/').takeIf { it.isNotEmpty() && it.endsWith(".svg") }

        val fileUploaded: Boolean
        val effectiveFileName: String?
        val effectiveSourceUrl: String
        if (newSvg != null) {
            effectiveFileName = newFileName
                ?: existingFileName
                ?: (if (newName != null) defaultFileName(newName) else defaultFileName(effectiveName))
            client.uploadDiagramFile(projectId, effectiveFileName, newSvg)
            effectiveSourceUrl = "${client.publicBaseUrl.trimEnd('/')}/project/$projectId/diagram/$effectiveFileName"
            fileUploaded = true
        } else if (newFileName != null && existingFileName != newFileName) {
            // Caller is renaming the file without supplying new content. Repoint the URL only.
            effectiveFileName = newFileName
            effectiveSourceUrl = "${client.publicBaseUrl.trimEnd('/')}/project/$projectId/diagram/$newFileName"
            fileUploaded = false
        } else {
            effectiveFileName = existingFileName
            effectiveSourceUrl = existingSourceUrl
            fileUploaded = false
        }

        // Mutate existing meta in place — preserves the server-emitted `type`
        // discriminator and any fields this MCP doesn't know about. Rebuilding
        // from scratch is what broke anchorBindings persistence in v0.0.5.
        val mergedMeta = existingMeta.toMutableMap()
        if (newName != null) mergedMeta["name"] = JsonPrimitive(newName)
        if (newDescription != null) mergedMeta["description"] = JsonPrimitive(newDescription)
        if (newBindings != null) mergedMeta["anchorBindings"] = newBindings
        if (effectiveSourceUrl != existingSourceUrl) mergedMeta["source"] = JsonPrimitive(effectiveSourceUrl)

        // Preserve the full existing Node, only overriding meta + state.
        val updated = JsonObject(existing.toMutableMap().apply {
            put("meta", JsonObject(mergedMeta))
            put("state", JsonPrimitive("NONE"))
        })
        client.postNode(updated)

        val effectiveBindings = (mergedMeta["anchorBindings"] as? JsonObject) ?: JsonObject(emptyMap())

        return buildJsonObject {
            put("server", client.serverId)
            put("diagramId", diagramId)
            put("projectId", projectId)
            put("fileName", effectiveFileName)
            put("source", effectiveSourceUrl)
            put("fileUploaded", fileUploaded)
            if (fileUploaded && newSvg != null) put("sourceBytes", newSvg.toByteArray(Charsets.UTF_8).size)
            put("anchorCount", effectiveBindings.size)
            put("anchorBindings", effectiveBindings)
            put("updated", JsonArray(buildList {
                if (newName != null) add(JsonPrimitive("name"))
                if (newDescription != null) add(JsonPrimitive("description"))
                if (newSvg != null) add(JsonPrimitive("source"))
                if (newFileName != null) add(JsonPrimitive("uploadFileName"))
                if (newBindings != null) add(JsonPrimitive("anchorBindings"))
            }))
        }
    }
}

/**
 * Fetch a Diagram node — including the SVG content behind its `source` URL.
 * This is the input the agent needs to propose improvements to an existing
 * dashboard.
 */
class GetDiagramTool(private val registry: KrillRegistry) : Tool {
    override val name = "get_diagram"
    override val description =
        "Fetch a Diagram node's metadata PLUS the SVG content pointed to by its `source` URL — for review or editing."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("diagramId") {
                put("type", "string")
                put("description", "Id of the KrillApp.Project.Diagram node.")
            }
        }
        putJsonArray("required") { add("diagramId") }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val diagramId = arguments["diagramId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: diagramId")

        val node = client.node(diagramId) as? JsonObject
            ?: error("Diagram $diagramId not found on server ${client.serverId}.")
        val nodeType = node["type"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
        if (nodeType != TYPE_DIAGRAM) error("Node $diagramId is not a Diagram (got $nodeType).")
        val meta = node["meta"] as? JsonObject ?: error("Node $diagramId has no meta object.")
        val projectId = node["parent"]?.jsonPrimitive?.contentOrNull

        val sourceUrl = meta["source"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val fileName = sourceUrl.substringAfterLast('/').takeIf { it.endsWith(".svg") }

        // Fetch the SVG if the source URL points at a /project/{id}/diagram/{file} on
        // this server. Any other URL (external CDN, old inline fragment) comes back null.
        val svg = if (projectId != null && fileName != null && sourceUrl.contains("/project/$projectId/diagram/$fileName")) {
            runCatching { client.downloadDiagramFile(projectId, fileName) }.getOrNull()
        } else null

        return buildJsonObject {
            put("server", client.serverId)
            put("diagramId", diagramId)
            put("projectId", projectId)
            put("name", meta["name"] ?: JsonNull)
            put("description", meta["description"] ?: JsonNull)
            put("source", sourceUrl)
            put("fileName", fileName)
            put("svg", svg)
            put("anchorBindings", meta["anchorBindings"] ?: JsonObject(emptyMap()))
        }
    }
}

/**
 * Raw SVG file upload to `/project/{id}/diagram/{file}` — bypasses the node
 * graph and just drops the file into the server's static content directory.
 * Useful when staging assets before deciding whether to wire them into a
 * Diagram node.
 */
class UploadDiagramFileTool(private val registry: KrillRegistry) : Tool {
    override val name = "upload_diagram_file"
    override val description =
        "Upload a raw SVG file to a Project's /diagram/{file} static path. No Diagram node is created. Use create_diagram for the full flow."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("projectId") {
                put("type", "string")
                put("description", "Id of the existing Project node that owns the file.")
            }
            putJsonObject("fileName") {
                put("type", "string")
                put("description", "SVG filename, e.g. 'aquarium.svg'. Must match ^[a-zA-Z0-9_.-]+$ and end with .svg.")
            }
            putJsonObject("source") {
                put("type", "string")
                put("description", "Raw SVG content (the bytes).")
            }
        }
        putJsonArray("required") {
            add("projectId")
            add("fileName")
            add("source")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val projectId = arguments["projectId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: projectId")
        val fileName = arguments["fileName"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: fileName")
        val svg = arguments["source"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: source")

        validateSvgFileName(fileName)
        if (!svg.contains("<svg", ignoreCase = true)) {
            error("source does not look like SVG (missing <svg> tag).")
        }

        client.uploadDiagramFile(projectId, fileName, svg)
        val url = "${client.publicBaseUrl.trimEnd('/')}/project/$projectId/diagram/$fileName"
        return buildJsonObject {
            put("server", client.serverId)
            put("projectId", projectId)
            put("fileName", fileName)
            put("url", url)
            put("bytes", svg.toByteArray(Charsets.UTF_8).size)
        }
    }
}

/** Download the raw SVG file from a Project's static path. */
class DownloadDiagramFileTool(private val registry: KrillRegistry) : Tool {
    override val name = "download_diagram_file"
    override val description =
        "Download a raw SVG file previously uploaded to /project/{id}/diagram/{file}."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("server") { put("type", "string") }
            putJsonObject("projectId") { put("type", "string") }
            putJsonObject("fileName") { put("type", "string") }
        }
        putJsonArray("required") {
            add("projectId")
            add("fileName")
        }
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val client = resolve(registry, arguments)
        val projectId = arguments["projectId"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: projectId")
        val fileName = arguments["fileName"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: fileName")

        val content = client.downloadDiagramFile(projectId, fileName)
        return buildJsonObject {
            put("server", client.serverId)
            put("projectId", projectId)
            put("fileName", fileName)
            put("svg", content)
            put("bytes", content.toByteArray(Charsets.UTF_8).size)
        }
    }
}

/**
 * Force the MCP to re-probe every configured seed and rebuild the registry.
 * Lets agents recover from the startup seed race without shell access.
 */
class ReseedServersTool(private val registry: KrillRegistry) : Tool {
    override val name = "reseed_servers"
    override val description =
        "Force krill-mcp to re-probe every configured seed in /etc/krill-mcp/config.json. Use when list_servers is empty after startup (common when krill-mcp came up before the krill server)."
    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    override suspend fun execute(arguments: JsonObject): JsonElement {
        val before = registry.all().size
        registry.reseed()
        val after = registry.all()
        return buildJsonObject {
            put("before", before)
            put("after", after.size)
            putJsonArray("servers") {
                after.forEach { c ->
                    addJsonObject {
                        put("id", c.serverId)
                        put("baseUrl", c.baseUrl)
                        put("publicBaseUrl", c.publicBaseUrl)
                    }
                }
            }
        }
    }
}

private suspend fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    registry.resolve(selector)?.let { return it }
    if (selector != null && KrillRegistry.looksLikeHost(selector)) {
        error("host unreachable: $selector — krill-mcp tried to lazy-register but /health did not respond (check DNS, the port in the selector (default 8442), and the swarm bearer).")
    }
    error("No Krill server matches '$selector' (and no default is registered). Try calling reseed_servers — the registry may have missed the initial probe.")
}

/** Slugify a human-readable name into `lowercase_snake_case.svg`. */
private fun defaultFileName(name: String): String {
    val slug = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifEmpty { "diagram" }
    return "$slug.svg"
}

/** Enforce the same character set the Krill server's upload route accepts. */
private fun validateSvgFileName(fileName: String) {
    require(fileName.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
        "fileName must contain only alphanumerics, dash, underscore, and dot (got '$fileName')."
    }
    require(fileName.lowercase().endsWith(".svg")) {
        "fileName must end with .svg (got '$fileName')."
    }
}
