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

/**
 * Convenience: list existing Project nodes on a server.
 */
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
 * Create a new `KrillApp.Project.Diagram` node with SVG `source` and an
 * `anchorBindings` map from SVG anchor ids (starting with `k_`) to node UUIDs.
 *
 * Optionally also uploads the raw SVG to `/project/{projectId}/diagram/{fileName}`
 * so the server's static content route can serve it — useful when another tool
 * (e.g. a webhook template) wants a stable URL.
 */
class CreateDiagramTool(private val registry: KrillRegistry) : Tool {
    override val name = "create_diagram"
    override val description =
        "Create a Diagram node (SVG dashboard with live-state anchors) under an existing Project."
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
                put("description", "Display name for the diagram.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional description.")
            }
            putJsonObject("source") {
                put("type", "string")
                put("description", "SVG content. Elements with id starting with 'k_' become anchors bound to nodes.")
            }
            putJsonObject("anchorBindings") {
                put("type", "object")
                put("description", "Map from SVG anchor id (e.g. 'k_temp_sensor') to the target node UUID.")
                putJsonObject("additionalProperties") { put("type", "string") }
            }
            putJsonObject("uploadFileName") {
                put("type", "string")
                put("description", "Optional filename (e.g. 'aquarium.svg'). If set, also PUT the SVG to /project/{projectId}/diagram/{fileName}.")
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
        val source = arguments["source"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: source")
        val bindings = arguments["anchorBindings"] as? JsonObject ?: JsonObject(emptyMap())
        val uploadFileName = arguments["uploadFileName"]?.jsonPrimitive?.contentOrNull

        if (!source.contains("<svg", ignoreCase = true)) {
            error("source does not look like SVG (missing <svg> tag).")
        }

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
                put("source", source)
                put("anchorBindings", bindings)
                put("error", "")
            }
            put("timestamp", 0L)
        }
        client.postNode(node)

        val fileUploaded = if (uploadFileName != null) {
            require(uploadFileName.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                "uploadFileName must contain only alphanumerics, dash, underscore, and dot."
            }
            require(uploadFileName.lowercase().endsWith(".svg")) { "uploadFileName must end with .svg" }
            client.uploadDiagramFile(projectId, uploadFileName, source)
            true
        } else false

        return buildJsonObject {
            put("server", client.serverId)
            put("projectId", projectId)
            put("diagramId", newId)
            put("name", name)
            put("anchorCount", bindings.size)
            put("fileUploaded", fileUploaded)
            if (fileUploaded) put("fileUrl", "$projectId/diagram/$uploadFileName")
        }
    }
}

/**
 * Update an existing Diagram node's `source` and/or `anchorBindings`.
 *
 * Other meta fields (name, description) are preserved — pass them explicitly
 * to change them.
 */
class UpdateDiagramTool(private val registry: KrillRegistry) : Tool {
    override val name = "update_diagram"
    override val description =
        "Update a Diagram node — typically to improve the SVG `source` or rewire `anchorBindings`."
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
                put("description", "Replacement SVG content. Omit to keep the current source.")
            }
            putJsonObject("anchorBindings") {
                put("type", "object")
                put("description", "Replacement anchor → node-id map. Omit to keep the current bindings.")
                putJsonObject("additionalProperties") { put("type", "string") }
            }
            putJsonObject("uploadFileName") {
                put("type", "string")
                put("description", "Optional filename to also PUT the new SVG at /project/{projectId}/diagram/{fileName}.")
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

        val newName = arguments["name"]?.jsonPrimitive?.contentOrNull
        val newDescription = arguments["description"]?.jsonPrimitive?.contentOrNull
        val newSource = arguments["source"]?.jsonPrimitive?.contentOrNull
        val newBindings = arguments["anchorBindings"] as? JsonObject
        val uploadFileName = arguments["uploadFileName"]?.jsonPrimitive?.contentOrNull

        if (newSource != null && !newSource.contains("<svg", ignoreCase = true)) {
            error("source does not look like SVG (missing <svg> tag).")
        }

        val mergedMeta = buildJsonObject {
            put("type", META_DIAGRAM)
            put("name", newName ?: existingMeta["name"]?.jsonPrimitive?.contentOrNull ?: "Diagram")
            put("description", newDescription ?: existingMeta["description"]?.jsonPrimitive?.contentOrNull ?: "")
            put("source", newSource ?: existingMeta["source"]?.jsonPrimitive?.contentOrNull ?: "")
            put("anchorBindings", newBindings ?: (existingMeta["anchorBindings"] as? JsonObject) ?: JsonObject(emptyMap()))
            put("error", "")
        }

        val updated = JsonObject(existing.toMutableMap().apply {
            put("meta", mergedMeta)
            put("state", JsonPrimitive("NONE"))
        })
        client.postNode(updated)

        val fileUploaded = if (uploadFileName != null && newSource != null) {
            require(uploadFileName.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                "uploadFileName must contain only alphanumerics, dash, underscore, and dot."
            }
            require(uploadFileName.lowercase().endsWith(".svg")) { "uploadFileName must end with .svg" }
            val projectId = existing["parent"]?.jsonPrimitive?.contentOrNull
                ?: error("Existing diagram has no parent project id.")
            client.uploadDiagramFile(projectId, uploadFileName, newSource)
            true
        } else false

        return buildJsonObject {
            put("server", client.serverId)
            put("diagramId", diagramId)
            put("updated", JsonArray(buildList {
                if (newName != null) add(JsonPrimitive("name"))
                if (newDescription != null) add(JsonPrimitive("description"))
                if (newSource != null) add(JsonPrimitive("source"))
                if (newBindings != null) add(JsonPrimitive("anchorBindings"))
            }))
            put("fileUploaded", fileUploaded)
        }
    }
}

/**
 * Fetch a Diagram's SVG source and anchor bindings — the full artifact Claude
 * needs to propose improvements to an existing dashboard.
 */
class GetDiagramTool(private val registry: KrillRegistry) : Tool {
    override val name = "get_diagram"
    override val description =
        "Download a Diagram node's SVG source, anchor bindings, name, and description for review or editing."
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

        return buildJsonObject {
            put("server", client.serverId)
            put("diagramId", diagramId)
            put("projectId", node["parent"] ?: JsonNull)
            put("name", meta["name"] ?: JsonNull)
            put("description", meta["description"] ?: JsonNull)
            put("source", meta["source"] ?: JsonNull)
            put("anchorBindings", meta["anchorBindings"] ?: JsonObject(emptyMap()))
        }
    }
}

/**
 * Raw SVG file upload to `/project/{id}/diagram/{file}` — bypasses the node
 * graph and just drops the file into the server's static content directory.
 * Useful when the caller wants to stage an SVG before deciding whether to
 * wire it up as a Diagram node.
 */
class UploadDiagramFileTool(private val registry: KrillRegistry) : Tool {
    override val name = "upload_diagram_file"
    override val description =
        "Upload a raw SVG file to a Project's /diagram/{file} static path (no Diagram node is created)."
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
                put("description", "SVG filename, e.g. 'aquarium.svg'. Must end with .svg.")
            }
            putJsonObject("source") {
                put("type", "string")
                put("description", "Raw SVG content.")
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
        val source = arguments["source"]?.jsonPrimitive?.contentOrNull
            ?: error("Missing required argument: source")

        require(fileName.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
            "fileName must contain only alphanumerics, dash, underscore, and dot."
        }
        require(fileName.lowercase().endsWith(".svg")) { "fileName must end with .svg" }
        if (!source.contains("<svg", ignoreCase = true)) {
            error("source does not look like SVG (missing <svg> tag).")
        }

        client.uploadDiagramFile(projectId, fileName, source)
        return buildJsonObject {
            put("server", client.serverId)
            put("projectId", projectId)
            put("fileName", fileName)
            put("path", "/project/$projectId/diagram/$fileName")
            put("bytes", source.toByteArray(Charsets.UTF_8).size)
        }
    }
}

/**
 * Download the raw SVG file from a Project's static path.
 */
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
            put("source", content)
            put("bytes", content.toByteArray(Charsets.UTF_8).size)
        }
    }
}

private fun resolve(registry: KrillRegistry, arguments: JsonObject): KrillClient {
    val selector = arguments["server"]?.jsonPrimitive?.contentOrNull
    return registry.resolve(selector)
        ?: error("No Krill server matches '$selector' (and no default is registered).")
}
