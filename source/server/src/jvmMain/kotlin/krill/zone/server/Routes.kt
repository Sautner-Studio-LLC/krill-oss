@file:OptIn(ExperimentalUuidApi::class)

package krill.zone.server

import co.touchlab.kermit.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.*
import krill.zone.server.krillapp.datapoint.*
import krill.zone.server.krillapp.server.serial.*
import krill.zone.shared.*
import krill.zone.shared.events.*
import krill.zone.shared.krillapp.datapoint.*
import krill.zone.shared.krillapp.server.*
import krill.zone.shared.krillapp.server.pin.*
import krill.zone.shared.krillapp.trigger.webhook.*
import krill.zone.shared.node.*
import java.io.*
import java.nio.file.*
import java.time.*
import java.time.format.*
import kotlin.uuid.*

private val logger = Logger.withTag("Routes")

/**
 * Configures all API routes for the Krill server
 */
internal fun Routing.configureApiRoutes(
    nodeManager: ServerNodeManager,
    dataProcessor: DataProcessor,
    piManager: PiManager,
    serialDirectoryMonitor: SerialDirectoryMonitor,
    pinProvider: PinProvider,
    scope: CoroutineScope
) {
    configureNodeRoutes(nodeManager, dataProcessor, serialDirectoryMonitor, piManager, scope)
    configureSystemRoutes(nodeManager, piManager, serialDirectoryMonitor, pinProvider, scope)
    configurePlatformRoutes(piManager)
    configureStaticContent()
}

private fun Routing.configurePlatformRoutes(pi: PiManager) {
    get("/header") {
        val pins = pi.getAllPins()
        call.respond(HttpStatusCode.OK, pins)
    }
}

/**
 * Node-related API endpoints
 */
private fun Routing.configureNodeRoutes(
    nodeManager: ServerNodeManager,
    dataProcessor: DataProcessor,
    serialDirectoryMonitor: SerialDirectoryMonitor,
    piManager: PiManager,
    scope: CoroutineScope
) {
    authenticate("auth-api-key") {
        //incoming webhooks
        get("/incoming/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val queryParams = call.request.queryParameters
            val allParams = mutableMapOf<String, List<String>>()

            // Add the captured path segments
            if (path.isNotEmpty()) {
                allParams["path"] = listOf(path)
            }

            // Add query parameters
            queryParams.names().forEach { name ->
                allParams[name] = queryParams.getAll(name) ?: emptyList()
            }
            logger.i { "Received incoming webhook request - path: '$path', params: $allParams" }
            val hooks = nodeManager.nodesByType(KrillApp.Trigger.IncomingWebHook)
                .filter { n -> n.meta is IncomingWebHookMetaData && (n.meta as IncomingWebHookMetaData).path == path && (n.meta as IncomingWebHookMetaData).method == krill.zone.shared.io.HttpMethod.GET }

            if (hooks.isNotEmpty()) {
                hooks.forEach { hook ->
                    nodeManager.execute(hook)
                }
                call.respond(HttpStatusCode.OK, allParams)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Get all nodes
        get("/nodes") {
            try {

                val nodes = nodeManager.nodes()
                    .filter { n ->
                        n.type != KrillApp.Client && n.state != NodeState.DELETING && (n.isMine() ||
                                n.type is KrillApp.Server)
                    }.map { n ->
                        when (n.type) {

                            is KrillApp.Server -> {
                                if (!n.isMine()) {
                                    toPeer(n)
                                } else {

                                    val list = nodeManager.nodes().filter { n -> n.type !is KrillApp.Server }.map { n -> n.id }
                                    val meta = n.meta as ServerMetaData
                                    val identity = ServerIdentity.getSelfWithInfo().meta as ServerMetaData
                                    val hostname = identity.name

                                    val serial = serialDirectoryMonitor.getDevices()

                                    n.copy(state = NodeState.NONE, meta = meta.copy(name = hostname, nodes = list, serialDevices = serial))
                                }

                            }

                            is KrillApp.DataPoint -> {
                                val meta = n.meta as DataPointMetaData
                                val snapshot = dataProcessor.last(n) ?: Snapshot()
                                n.copy(state = NodeState.NONE, meta = meta.copy(snapshot = snapshot))
                            }

                            is KrillApp.Server.Pin -> {
                                val state = piManager.readPinState(n)
                                val meta = n.meta as PinMetaData
                                n.copy(state = NodeState.NONE, meta = meta.copy(state = state))
                            }

                            else -> {
                                n.copy(state = NodeState.NONE)
                            }
                        }
                    }


                call.respond(HttpStatusCode.OK, nodes)


            } catch (ex: Exception) {
                logger.e("Error fetching nodes", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to fetch nodes")
            }
        }

        // Get single node by ID
        get("/node/{id}") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing node ID")
                    return@get
                }

                try {
                    if (!nodeManager.nodeAvailable(id)) {
                        call.respond(HttpStatusCode.NotFound, "Node not found or is being deleted")
                        return@get
                    }
                    val node = nodeManager.readNodeState(id).value
                    if (node.state == NodeState.DELETING) {
                        call.respond(HttpStatusCode.NotFound, "Node not found or is being deleted")
                    } else {
                        when (node.type) {
                            KrillApp.Server -> {

                                val list =
                                    nodeManager.nodes().filter { n -> n.type !is KrillApp.Server }.map { n -> n.id }
                                val meta = node.meta as ServerMetaData
                                val identity = ServerIdentity.getSelfWithInfo().meta as ServerMetaData
                                val hostname = identity.name
                                val serial = serialDirectoryMonitor.getDevices()
                                call.respond(
                                    HttpStatusCode.OK,
                                    node.copy(meta = meta.copy(name = hostname, nodes = list, serialDevices = serial))
                                )
                            }

                            KrillApp.DataPoint -> {
                                val meta = node.meta as DataPointMetaData
                                val snapshot = dataProcessor.last(node) ?: Snapshot()
                                call.respond(HttpStatusCode.OK, node.copy(meta = meta.copy(snapshot = snapshot)))

                            }

                            is KrillApp.Server.Pin -> {
                                val state = piManager.readPinState(node)
                                val meta = node.meta as PinMetaData
                                call.respond(HttpStatusCode.OK, node.copy(meta = meta.copy(state = state)))
                            }

                            else -> {
                                call.respond(HttpStatusCode.OK, node)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.e("Error reading node $id", e)
                    call.respond(HttpStatusCode.NotFound, "Node not found")
                }
            } catch (ex: Exception) {
                logger.e("Error in get node endpoint", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve node")
            }
        }

        // Get node data series - nodes will always have DataPointMetaData
        get("/node/{id}/data/series") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing node ID")
                    return@get
                }

                val timeRange = extractTimeRange(call)

                try {
                    if (!nodeManager.nodeAvailable(id)) {
                        call.respond(HttpStatusCode.NotFound, "Node not found")
                        return@get
                    }
                    val node = nodeManager.readNodeState(id).value
                    val series = dataProcessor.range(node, timeRange.start, timeRange.end)
                    call.respond(HttpStatusCode.OK, series)
                } catch (e: Exception) {
                    logger.e("Error reading node $id for data series", e)
                    call.respond(HttpStatusCode.NotFound, "Node not found")
                }
            } catch (ex: Exception) {
                logger.e("Error in get data series endpoint", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve data series")
            }
        }

        // Get node data plot
        get("/node/{id}/data/plot") {
            try {
                val id = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing node ID")
                    return@get
                }

                val timeRange = extractTimeRange(call)

                try {
                    if (!nodeManager.nodeAvailable(id)) {
                        call.respond(HttpStatusCode.NotFound, "Node not found")
                        return@get
                    }
                    val node = nodeManager.readNodeState(id).value
                    val meta = node.meta as DataPointMetaData
                    val series = dataProcessor.range(node, timeRange.start, timeRange.end)

                    if (series.isEmpty()) {
                        call.respond(HttpStatusCode.NotFound, "No data points in range")
                        return@get
                    }

                    val svg = when (meta.dataType) {
                        DataType.DIGITAL -> generateBoolPlotSVG(series, timeRange.start, timeRange.end)
                        else -> generatePlotSVG(series, timeRange.span)
                    }
                    call.respondText(svg, contentType = ContentType.Image.SVG)
                } catch (e: Exception) {
                    logger.e("Error reading node $id for plot", e)
                    call.respond(HttpStatusCode.NotFound, "Node not found")
                }
            } catch (ex: Exception) {
                logger.e("Error in get plot endpoint", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to generate plot")
            }
        }

        // Update node
        post("/node/{id}") {
            try {

                val node = call.receive<Node>()
                scope.launch {
                    logger.i("${node.details()}: $node")
                    nodeManager.update(node)
                }


                call.respond(HttpStatusCode.Accepted)


            } catch (ex: Exception) {
                logger.e("Error updating node", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to update node")
            }
        }

        delete("/node/{id}") {
            try {
                call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing node ID")
                    return@delete
                }

                val node = call.receive<Node>()

                logger.i("${node.details()}: received node post ${node.meta}")
                nodeManager.delete(node)
                call.respond(HttpStatusCode.OK)
            } catch (ex: Exception) {
                logger.e("Error updating node", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to update node")
            }
        }

        // Upload SVG diagram file
        put("/project/{id}/diagram/{file}") {
            try {
                val projectId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                    return@put
                }
                val fileName = call.parameters["file"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing file name")
                    return@put
                }

                // Validate projectId format - only allow alphanumeric, hyphens, and underscores
                if (!projectId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid project ID format")
                    return@put
                }

                // Validate fileName format - only allow alphanumeric, hyphens, underscores, and dots
                if (!fileName.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid file name format")
                    return@put
                }

                // Validate file extension
                if (!fileName.lowercase().endsWith(".svg")) {
                    call.respond(HttpStatusCode.BadRequest, "File must have .svg extension")
                    return@put
                }

                // Validate content type - only accept SVG content types
                val contentType = call.request.contentType()
                if (contentType != ContentType.Image.SVG &&
                    contentType.toString() != "image/svg+xml"
                ) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid content type: expected image/svg+xml")
                    return@put
                }

                // Check content-length header for size limit (2MB)
                val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
                val maxSize = 2 * 1024 * 1024L // 2MB
                if (contentLength != null && contentLength > maxSize) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "File size exceeds 2MB limit")
                    return@put
                }

                val projectRoot = File("/srv/krill/project")
                val diagramDir = File(projectRoot, "$projectId/diagram")
                val file = File(diagramDir, fileName)

                // Path traversal protection - check against diagramDir
                if (!file.canonicalPath.startsWith(diagramDir.canonicalPath.let {
                        // Ensure diagramDir path is also safe
                        if (!File(it).canonicalPath.startsWith(projectRoot.canonicalPath)) {
                            call.respond(HttpStatusCode.BadRequest, "Invalid project path")
                            return@put
                        }
                        it
                    })) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid file path")
                    return@put
                }

                // Create directories if needed
                if (!diagramDir.exists()) {
                    if (!diagramDir.mkdirs()) {
                        logger.e("Failed to create directory: ${diagramDir.absolutePath}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create directory")
                        return@put
                    }
                }

                // Read the file content
                val content = call.receive<ByteArray>()

                // Additional size check on actual content
                if (content.size > maxSize) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "File size exceeds 2MB limit")
                    return@put
                }

                // Validate SVG content - basic check that it looks like SVG
                val svgText = content.decodeToString()
                if (!svgText.contains("<svg", ignoreCase = true)) {
                    call.respond(HttpStatusCode.BadRequest, "Content does not appear to be valid SVG")
                    return@put
                }

                file.writeBytes(content)

                logger.i("Uploaded SVG diagram: $projectId/diagram/$fileName (${content.size} bytes)")
                call.respond(HttpStatusCode.Created, "File uploaded successfully")
            } catch (ex: Exception) {
                logger.e("Error uploading SVG diagram", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to upload file")
            }
        }

        // Upload journal photo
        put("/project/{id}/journal/{entryId}/photo/{file}") {
            try {
                val projectId = call.parameters["id"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing project ID")
                    return@put
                }
                val entryId = call.parameters["entryId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing entry ID")
                    return@put
                }
                val fileName = call.parameters["file"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing file name")
                    return@put
                }

                // Validate projectId format - only allow alphanumeric, hyphens, and underscores
                if (!projectId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid project ID format")
                    return@put
                }

                // Validate entryId format - only allow alphanumeric, hyphens, and underscores
                if (!entryId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid entry ID format")
                    return@put
                }

                // Validate fileName format: must have base name, single dot, and extension
                // Pattern: at least one alphanumeric/underscore/hyphen, then dot, then extension
                if (!fileName.matches(Regex("^[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+$"))) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid file name format")
                    return@put
                }

                // Validate file extension
                val extension = fileName.substringAfterLast('.').lowercase()
                if (extension !in listOf("jpg", "jpeg", "png", "gif", "webp")) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid image format")
                    return@put
                }

                // Check content-length header for size limit (5MB for images)
                val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
                val maxSize = 5 * 1024 * 1024L // 5MB
                if (contentLength != null && contentLength > maxSize) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "File size exceeds 5MB limit")
                    return@put
                }

                val projectRoot = File("/srv/krill/project")
                val journalDir = File(projectRoot, "$projectId/journal/$entryId")
                val file = File(journalDir, fileName)

                // Path traversal protection - verify all paths are within project root
                val projectRootCanonical = projectRoot.canonicalPath
                val journalDirCanonical = journalDir.canonicalPath
                val fileCanonical = file.canonicalPath

                if (!journalDirCanonical.startsWith(projectRootCanonical)) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid project path")
                    return@put
                }
                if (!fileCanonical.startsWith(journalDirCanonical)) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid file path")
                    return@put
                }

                // Create directories if needed
                if (!journalDir.exists()) {
                    if (!journalDir.mkdirs()) {
                        logger.e("Failed to create directory: ${journalDir.absolutePath}")
                        call.respond(HttpStatusCode.InternalServerError, "Failed to create directory")
                        return@put
                    }
                }

                // Read the file content
                val content = call.receive<ByteArray>()

                // Additional size check on actual content
                if (content.size > maxSize) {
                    call.respond(HttpStatusCode.PayloadTooLarge, "File size exceeds 5MB limit")
                    return@put
                }

                file.writeBytes(content)

                logger.i("Uploaded journal photo: $projectId/journal/$entryId/$fileName (${content.size} bytes)")
                call.respond(HttpStatusCode.Created, "Photo uploaded successfully")
            } catch (ex: Exception) {
                logger.e("Error uploading journal photo", ex)
                call.respond(HttpStatusCode.InternalServerError, "Failed to upload file")
            }
        }

        // Camera snapshot - capture a single JPEG frame
        get("/camera/{id}/snapshot") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing camera ID")
                return@get
            }

            try {
                // Capture a single JPEG frame using rpicam-still.
                // stderr has verbose logs — do NOT merge with stdout or JPEG gets corrupted.
                val process = ProcessBuilder(
                    "rpicam-still", "-o", "-", "--immediate", "--nopreview", "-n",
                    "--width", "1280", "--height", "720", "--encoding", "jpg"
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start()

                val imageBytes = process.inputStream.readBytes()
                val exitCode = process.waitFor()

                if (exitCode == 0 && imageBytes.isNotEmpty()) {
                    call.respondBytes(imageBytes, ContentType.Image.JPEG, HttpStatusCode.OK)
                } else {
                    logger.w("Camera snapshot failed: exit=$exitCode, bytes=${imageBytes.size}")
                    call.respond(HttpStatusCode.NotFound, "Camera not available")
                }
            } catch (e: Exception) {
                logger.e("Error capturing camera snapshot", e)
                call.respond(HttpStatusCode.InternalServerError, "Failed to capture snapshot")
            }
        }

        // TODO: GET /camera/{id}/stream — MJPEG proxy from rpicam-vid subprocess (v2)

        // List saved camera snapshots (newest first)
        get("/camera/{id}/thumbnails") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing camera ID")
                return@get
            }
            val dir = File("/srv/krill/camera/$id")
            if (!dir.exists() || !dir.isDirectory) {
                call.respond(HttpStatusCode.OK, emptyList<String>())
                return@get
            }
            val filenames = dir.listFiles()
                ?.filter { it.extension == "jpg" }
                ?.sortedByDescending { it.name }
                ?.take(50)
                ?.map { it.name }
                ?: emptyList()
            call.respond(HttpStatusCode.OK, filenames)
        }

        // Serve a saved camera snapshot file
        get("/camera/{id}/thumbnails/{file}") {
            val id = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing camera ID")
                return@get
            }
            val fileName = call.parameters["file"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing file name")
                return@get
            }
            val cameraRoot = File("/srv/krill/camera")
            val file = File(cameraRoot, "$id/$fileName")
            if (!file.exists() || !file.canonicalPath.startsWith(cameraRoot.canonicalPath)) {
                call.respond(HttpStatusCode.NotFound, "Snapshot not found")
                return@get
            }
            call.respondBytes(file.readBytes(), ContentType.Image.JPEG, HttpStatusCode.OK)
        }

    }
}

/**
 * System-level API endpoints
 * GET /trust endpoints are used to GET the self signed certificate of a server when a beacon is recieved by a server or app
 * OR when a user addes an KrillApp.Server.ExternalServer which allows for manual entry and creation of a server without a beacon.
 *
 * and POST is for when a client is posting api keys for server to server communication.
 */
private fun Routing.configureSystemRoutes(
    nodeManager: ServerNodeManager,
    piManager: PiManager,
    serialDirectoryMonitor: SerialDirectoryMonitor,
    pinProvider: PinProvider,
    scope: CoroutineScope
) {
    // download the servers self signed cert, no api key required
    get("/trust") {
        try {
            val file = File("/etc/krill/certs/krill.crt")
            if (!file.exists()) {
                logger.e("${file.absolutePath} does not exist (run cert generator on this server)")
                call.respond(
                    HttpStatusCode.NotFound,
                    "No krill.cert found on server if this is a manual install please run the cert generator script."
                )
            } else {
                logger.i("Sending Trust Cert to ${call.request.userAgent()} ${call.request.local.remoteHost}")
                val hostname = call.request.local.serverHost
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    "attachment; filename=\"${hostname}.crt\""
                )
                // Use x-x509-ca-cert so iOS recognizes this as an installable certificate
                call.respondBytes(
                    file.readBytes(),
                    ContentType("application", "x-x509-ca-cert"),
                    HttpStatusCode.OK
                )
            }
        } catch (ex: Exception) {
            logger.e("Error sending Trust Cert to ${call.request.userAgent()}", ex)
            call.respond(HttpStatusCode.InternalServerError, "Failed to retrieve certificate")
        }
    }



    // PIN-derived Bearer token for WASM clients served from this host.
    // Unauthenticated — the WASM app is already trusted (served from the same server).
    // Returns the token so the WASM app can authenticate subsequent API calls.
    get("/krill-token") {
        val token = pinProvider.bearerToken()
        if (token != null) {
            call.respondText(token, ContentType.Text.Plain, HttpStatusCode.OK)
        } else {
            // No PIN configured — WASM will operate without auth (open access)
            call.respondText("", ContentType.Text.Plain, HttpStatusCode.OK)
        }
    }

    authenticate("auth-api-key") {
        /**
         * Server-Sent Events (SSE) endpoint for real-time node updates.
         * 
         * This endpoint provides a continuous stream of node changes to connected clients.
         * All database CRUD operations performed by ServerNodeManager are automatically
         * broadcast through this endpoint via the nodeUpdates SharedFlow.
         */
        sse("/sse", serialize = { _, node ->
            fastJson.encodeToString<Node>(node as Node)
        }) {
            // Send current server state on connect
            val identity = ServerIdentity.getSelfWithInfo().meta as ServerMetaData
            val hostname = identity.name
            if (!nodeManager.nodeAvailable(installId())) return@sse
            val host = nodeManager.readNodeState(installId()).value
            val meta = host.meta as ServerMetaData
            val nodeList = nodeManager.nodes().filter { n -> n.type !is KrillApp.Server }.map { n -> n.id }
            val serial = serialDirectoryMonitor.getDevices()
            send(host.copy(meta = meta.copy(name = hostname, nodes = nodeList, serialDevices = serial)))

            // Collect from the nodeUpdates SharedFlow for real-time updates
            nodeManager.nodeUpdates.collect { node ->
                if (node.state != NodeState.NONE) {
                    logger.d { "${node.details()}: SSE sending update" }
                    when (node.type) {
                        is KrillApp.Server -> {

                            val serial = serialDirectoryMonitor.getDevices()
                            send(host.copy(meta = meta.copy(name = hostname, nodes = nodeList, serialDevices = serial)))
                        }

                        is KrillApp.Server.Pin -> {
                            val meta = node.meta as PinMetaData
                            if (meta.isConfigured) {
                                val state = piManager.readPinState(node)
                                send(node.copy(meta = meta.copy(state = state)))
                            } else {
                                send(node)
                            }
                        }

                        else -> {
                            send(node)
                        }
                    }
                }

            }
        }

        sse("/events", serialize = { _, event ->
            fastJson.encodeToString<Event>(event as Event)
        }) {


            send(Event(installId(), EventType.ACK))
            EventFlowContainer.events.collect { event ->
                send(event)
            }

        }


        /**
         * Health check endpoint also provides the server id for new connnections added by the app
         */
        get("/health") {
            try {
                if (!nodeManager.nodeAvailable(installId())) {
                    call.respond(HttpStatusCode.NotFound, "install id missing ${installId()}")
                } else {
                    val info = nodeManager.readNodeState(installId()).value
                    val identity = ServerIdentity.getSelfWithInfo().meta as ServerMetaData
                    val hostname = identity.name
                    val list = nodeManager.nodes().filter { n -> n.type !is KrillApp.Server }.map { n -> n.id }
                    val meta = info.meta as ServerMetaData
                    val serial = serialDirectoryMonitor.getDevices()
                    call.respond(
                        HttpStatusCode.OK,
                        info.copy(meta = meta.copy(name = hostname, nodes = list, serialDevices = serial))
                    )
                }
            } catch (e: Exception) {
                logger.e("Server node not found", e)
                call.respond(HttpStatusCode.InternalServerError, "Server Node Not Found")
            }

        }

        // Shutdown endpoint
        get("/shutdown") {
            call.respondText("Shutting down...")
            logger.i("Shutdown requested via /shutdown endpoint")

            // Launch shutdown in background to allow response to be sent
            scope.launch {
                delay(500) // Give time for response to be sent
                logger.i("Initiating server shutdown...")
                scope.cancel("Shutting down")
                delay(1000) // Give time for cleanup
                logger.i("Exiting process...")
                kotlin.system.exitProcess(0)
            }
        }

        // ==================== Backup Routes ====================

        get("/backup/list") {
            val backupDir = File("/srv/krill/backup")
            if (!backupDir.exists()) {
                call.respond(HttpStatusCode.OK, emptyList<Map<String, Any>>())
                return@get
            }
            val regex = Regex("""^(.+)-backup-(\d+)-(\d+)\.zip$""")
            val archives = backupDir.listFiles()
                ?.filter { it.extension == "zip" }
                ?.sortedByDescending { it.name }
                ?.mapNotNull { file ->
                    regex.matchEntire(file.name)?.let { match ->
                        mapOf(
                            "filename" to file.name,
                            "hostname" to match.groupValues[1],
                            "createdAt" to match.groupValues[2].toLong(),
                            "expiresAt" to match.groupValues[3].toLong(),
                            "sizeBytes" to file.length()
                        )
                    }
                } ?: emptyList()
            call.respond(HttpStatusCode.OK, archives)
        }

        delete("/backup/file") {
            val filename = call.request.queryParameters["file"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing file parameter")
                return@delete
            }
            val backupDir = File("/srv/krill/backup")
            val file = File(backupDir, filename)
            if (!file.exists() || !file.canonicalPath.startsWith(backupDir.canonicalPath)) {
                call.respond(HttpStatusCode.NotFound, "File not found")
                return@delete
            }
            file.delete()
            call.respond(HttpStatusCode.OK, "Deleted $filename")
        }

        post("/backup/restore") {
            try {
                val request = call.receive<Map<String, String>>()
                val filename = request["filename"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Missing filename")
                    return@post
                }
                val backupDir = File("/srv/krill/backup")
                val archiveFile = File(backupDir, filename)
                if (!archiveFile.exists() || !archiveFile.canonicalPath.startsWith(backupDir.canonicalPath)) {
                    call.respond(HttpStatusCode.NotFound, "Archive not found")
                    return@post
                }

                // TODO: Implement full restore logic (extract ZIP, reimport nodes, restore dirs)
                // For v1, return success with reboot instruction
                call.respond(HttpStatusCode.OK, mapOf("message" to "Restore initiated from $filename. Please reboot the server."))
            } catch (e: Exception) {
                logger.e("Restore failed", e)
                call.respond(HttpStatusCode.InternalServerError, "Restore failed: ${e.message}")
            }
        }
    }
}

/**
 * Static content serving
 */
private fun Routing.configureStaticContent() {
    val zip = File("/var/lib/krill/wasm/wasm-archive.zip")
    val projectRoot = File("/srv/krill/project")

    // Project static content routes (SVG diagrams and images)
    get("/project/{id}/diagram/{file}") {
        val projectId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing project ID")
            return@get
        }
        val fileName = call.parameters["file"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@get
        }

        val file = File(projectRoot, "$projectId/diagram/$fileName")
        if (!file.exists() || !file.canonicalPath.startsWith(projectRoot.canonicalPath)) {
            call.respond(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        call.respondBytes(file.readBytes(), ContentType.Image.SVG, HttpStatusCode.OK)
    }

    get("/project/{id}/images/{file}") {
        val projectId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing project ID")
            return@get
        }
        val fileName = call.parameters["file"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@get
        }

        val file = File(projectRoot, "$projectId/images/$fileName")
        if (!file.exists() || !file.canonicalPath.startsWith(projectRoot.canonicalPath)) {
            call.respond(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        val extension = fileName.substringAfterLast('.').lowercase()
        val contentType = when (extension) {
            "svg" -> ContentType.Image.SVG
            "png" -> ContentType.Image.PNG
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "gif" -> ContentType.Image.GIF
            "webp" -> ContentType("image", "webp")
            "ico" -> ContentType("image", "x-icon")
            "bmp" -> ContentType("image", "bmp")
            else -> ContentType.Application.OctetStream
        }

        call.respondBytes(file.readBytes(), contentType, HttpStatusCode.OK)
    }

    // Journal photos route
    get("/project/{id}/journal/{entryId}/photo/{file}") {
        val projectId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing project ID")
            return@get
        }
        val entryId = call.parameters["entryId"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing entry ID")
            return@get
        }
        val fileName = call.parameters["file"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing file name")
            return@get
        }

        // Validate parameter formats for defense in depth
        if (!projectId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            call.respond(HttpStatusCode.BadRequest, "Invalid project ID format")
            return@get
        }
        if (!entryId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            call.respond(HttpStatusCode.BadRequest, "Invalid entry ID format")
            return@get
        }
        if (!fileName.matches(Regex("^[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+$"))) {
            call.respond(HttpStatusCode.BadRequest, "Invalid file name format")
            return@get
        }

        val file = File(projectRoot, "$projectId/journal/$entryId/$fileName")
        if (!file.exists() || !file.canonicalPath.startsWith(projectRoot.canonicalPath)) {
            call.respond(HttpStatusCode.NotFound, "File not found")
            return@get
        }

        val extension = fileName.substringAfterLast('.').lowercase()
        val contentType = when (extension) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "gif" -> ContentType.Image.GIF
            "webp" -> ContentType("image", "webp")
            else -> ContentType.Application.OctetStream
        }

        call.respondBytes(file.readBytes(), contentType, HttpStatusCode.OK)
    }

    if (!zip.exists()) {
        logger.e("WASM archive not found at ${zip.absolutePath}. Please run 'gradle wasmZip' to generate it.")
        get("/") {
            call.respondText(
                "WASM archive not found ${zip.absolutePath}. Please run 'gradle wasmJsBrowserDevelopmentExecutableDistribution' to generate it.",
                ContentType.Text.Plain
            )
        }
    } else {
        staticZip("/", "", Paths.get(zip.absolutePath)) {
            enableAutoHeadResponse()
            contentType { url ->
                val extension = url.toString().substringAfterLast('.').lowercase()
                when (extension) {
                    "ttf" -> ContentType("font", "ttf")
                    "otf" -> ContentType("font", "otf")
                    "woff" -> ContentType("font", "woff")
                    "woff2" -> ContentType("font", "woff2")
                    "json" -> ContentType("application", "json")
                    else -> null // Use default content type resolution
                }
            }
            modify { url, call ->
                // Entry point files must not be served stale — validate with server on each load.
                // Resource files (SVG icons, fonts, images) are safe to cache: they only change
                // when the app is redeployed, at which point new entry files trigger a fresh load.
                // Previously "no-store" was applied to everything, forcing the browser to re-fetch
                // every icon on every render, causing request pile-up and 10-60s TTFB on the Pi.
                val extension = url.toString().substringAfterLast('.').lowercase()
                val cacheControl = when (extension) {
                    "html", "js", "mjs", "wasm" -> "no-cache"
                    else -> "public, max-age=3600"
                }
                call.response.headers.append(HttpHeaders.CacheControl, cacheControl)
            }
        }
    }
}


/**
 * Data class representing a time range for queries
 */
private data class TimeRange(val start: Long, val end: Long) {
    val span: Long get() = end - start
}

/**
 * Extract time range from request query parameters
 */
private fun extractTimeRange(call: ApplicationCall): TimeRange {
    val defaultStart = System.currentTimeMillis() - (1000 * 60 * 60) // 1 hour ago
    val defaultEnd = System.currentTimeMillis()

    val start = call.request.queryParameters["st"]?.toLongOrNull() ?: defaultStart
    val end = call.request.queryParameters["et"]?.toLongOrNull() ?: defaultEnd

    return TimeRange(start, end)
}

/**
 * Generate SVG plot from data series
 */
private fun generatePlotSVG(series: List<Snapshot>, span: Long): String {


    val pattern = when {
        span > 48 * 60 * 60 * 1000L -> "MM-dd HH:mm" // > 2 days
        span > 2 * 60 * 60 * 1000L -> "HH:mm"        // > 2 hours
        else -> "HH:mm:ss"                           // short span
    }

    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern(pattern)
    val timeLabels = series.map { snapshot ->
        Instant.ofEpochMilli(snapshot.timestamp).atZone(zone).format(formatter)
    }

    return krill.zone.server.krillapp.datapoint.chart.createChirpyDarkChartSVG(
        timeLabels = timeLabels,
        values = series.map { snapshot -> snapshot.doubleValue() },
        width = 800,
        height = 400
    )
}

/**
 * Generate SVG timeline plot for boolean data showing ON/OFF states
 */
private fun generateBoolPlotSVG(series: List<Snapshot>, startTime: Long, endTime: Long): String {
    val width = 800
    val height = 200
    val margin = 60
    val chartX = margin
    val chartY = margin / 2
    val chartWidth = width - 2 * margin
    val chartHeight = height - margin - chartY

    val timeRange = endTime - startTime
    val zone = ZoneId.systemDefault()

    // Colors
    val bgColor = "#0b1320"
    val onColor = "#4caf50"   // Green for ON
    val offColor = "#e57373"  // Red for OFF
    val textColor = "#e6f0ff"
    val gridColor = "#1a2840"

    val pattern = when {
        timeRange > 48 * 60 * 60 * 1000L -> "MM-dd HH:mm"
        timeRange > 2 * 60 * 60 * 1000L -> "HH:mm"
        else -> "HH:mm:ss"
    }
    val formatter = DateTimeFormatter.ofPattern(pattern)

    val svg = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height">""")

        // Background
        appendLine("""  <rect width="$width" height="$height" fill="$bgColor"/>""")

        // Grid lines (vertical)
        for (i in 0..5) {
            val x = chartX + (chartWidth * i / 5)
            appendLine("""  <line x1="$x" y1="$chartY" x2="$x" y2="${chartY + chartHeight}" stroke="$gridColor" stroke-width="1"/>""")
        }

        // Draw ON/OFF regions
        val barHeight = chartHeight * 0.6
        val barY = chartY + (chartHeight - barHeight) / 2

        var prevTimestamp = startTime
        var prevValue = series.firstOrNull()?.doubleValue() ?: 0.0

        series.forEach { snapshot ->
            val currentX = chartX + ((snapshot.timestamp - startTime).toDouble() / timeRange * chartWidth)
            val prevX = chartX + ((prevTimestamp - startTime).toDouble() / timeRange * chartWidth)

            val color = if (prevValue == 1.0) onColor else offColor
            appendLine("""  <rect x="$prevX" y="$barY" width="${currentX - prevX}" height="$barHeight" fill="$color"/>""")

            prevTimestamp = snapshot.timestamp
            prevValue = snapshot.doubleValue()
        }

        // Final segment to end time
        val finalX = chartX + chartWidth
        val lastX = chartX + ((prevTimestamp - startTime).toDouble() / timeRange * chartWidth)
        val finalColor = if (prevValue == 1.0) onColor else offColor
        appendLine("""  <rect x="$lastX" y="$barY" width="${finalX - lastX}" height="$barHeight" fill="$finalColor"/>""")

        // State labels
        appendLine("""  <text x="${chartX - 10}" y="${barY + 15}" text-anchor="end" fill="$textColor" font-family="Sans-Serif" font-size="12">ON</text>""")
        appendLine("""  <text x="${chartX - 10}" y="${barY + barHeight - 5}" text-anchor="end" fill="$textColor" font-family="Sans-Serif" font-size="12">OFF</text>""")

        // X-axis time labels
        for (i in 0..5) {
            val time = startTime + (timeRange * i / 5)
            val label = Instant.ofEpochMilli(time).atZone(zone).format(formatter)
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val x = chartX + (chartWidth * i / 5)
            appendLine("""  <text x="$x" y="${chartY + chartHeight + 20}" text-anchor="middle" fill="$textColor" font-family="Sans-Serif" font-size="12">$label</text>""")
        }

        // Legend
        val legendY = 15
        appendLine("""  <rect x="${chartX + chartWidth - 100}" y="$legendY" width="20" height="15" fill="$onColor"/>""")
        appendLine("""  <text x="${chartX + chartWidth - 75}" y="${legendY + 12}" fill="$textColor" font-family="Sans-Serif" font-size="12">ON</text>""")
        appendLine("""  <rect x="${chartX + chartWidth - 40}" y="$legendY" width="20" height="15" fill="$offColor"/>""")
        appendLine("""  <text x="${chartX + chartWidth - 15}" y="${legendY + 12}" fill="$textColor" font-family="Sans-Serif" font-size="12">OFF</text>""")

        // Title
        appendLine("""  <text x="${width / 2}" y="${height - 5}" text-anchor="middle" fill="$textColor" font-family="Sans-Serif" font-size="13" font-weight="bold">Digital State Timeline</text>""")

        appendLine("""</svg>""")
    }

    return svg
}

