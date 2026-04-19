package krill.zone.mcp.http

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.mcp.McpServer
import kotlinx.serialization.json.*
import org.slf4j.event.Level

fun startMcpServer(
    listenPort: Int,
    mcp: McpServer,
    pin: PinProvider,
): EmbeddedServer<*, *> {
    val server = embeddedServer(Netty, port = listenPort, host = "0.0.0.0") {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(CallLogging) {
            level = Level.INFO
            filter { call -> call.request.path().startsWith("/mcp") }
        }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.application.environment.log.warn("Unhandled error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (cause.message ?: "Internal error")),
                )
            }
        }
        routing { mcpRoutes(mcp, pin) }
    }
    server.start(wait = false)
    return server
}

private fun Routing.mcpRoutes(mcp: McpServer, pin: PinProvider) {
    // Liveness — no auth. Returns whether a PIN has been configured.
    get("/healthz") {
        call.respond(
            HttpStatusCode.OK,
            mapOf(
                "ok" to true,
                "pinConfigured" to pin.isConfigured(),
            ),
        )
    }

    // MCP Streamable HTTP endpoint. Single POST, JSON-RPC body.
    post("/mcp") {
        if (!authorized(call, pin)) {
            call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"krill-mcp\"")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing or invalid bearer token"))
            return@post
        }

        val text = call.receiveText()
        val parsed = runCatching { Json.parseToJsonElement(text) }.getOrElse {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
            return@post
        }

        val response = mcp.handle(parsed)
        if (response == null) {
            // JSON-RPC notification: no body, 202 per Streamable HTTP spec.
            call.respond(HttpStatusCode.Accepted)
        } else {
            call.respondText(
                text = response.toString(),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
    }

    // MCP clients may probe with GET on the endpoint; advertise we're alive.
    get("/mcp") {
        call.respond(HttpStatusCode.MethodNotAllowed, mapOf("error" to "Use POST for JSON-RPC"))
    }
}

private fun authorized(call: ApplicationCall, pin: PinProvider): Boolean {
    val header = call.request.headers[HttpHeaders.Authorization] ?: return false
    val token = header.removePrefix("Bearer ").trim()
    if (token == header || token.isEmpty()) return false
    return pin.validate(token)
}
