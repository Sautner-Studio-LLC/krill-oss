package krill.zone.shared.krillapp.server

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for the `ServerMetaData.demoMode` capability flag
 * (krill-oss#208 — lets clients detect a read-only public demo server so they
 * can skip FTUE and hide edit affordances).
 *
 * Covers:
 *  - the flag defaults to `false` (a normal server);
 *  - it round-trips through JSON;
 *  - old wire payloads that predate the field still deserialize (defaulting to
 *    `false`), so a new client talking to an old server is unaffected.
 */
class ServerMetaDataDemoModeTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `demoMode defaults to false`() {
        assertFalse(ServerMetaData().demoMode)
    }

    @Test
    fun `demoMode round-trips through JSON`() {
        val encoded = json.encodeToString(ServerMetaData.serializer(), ServerMetaData(demoMode = true))
        assertTrue(encoded.contains("\"demoMode\":true"))
        val decoded = json.decodeFromString(ServerMetaData.serializer(), encoded)
        assertTrue(decoded.demoMode)
    }

    @Test
    fun `payload without demoMode deserializes with false default`() {
        val oldPayload = """
            {
              "name": "example-host",
              "port": 8442,
              "version": "1.0.0",
              "loggingEnabled": false,
              "beaconsEnabled": true,
              "error": "",
              "sources": [],
              "snapshot": {"timestamp": 0, "value": ""},
              "invocationTriggers": [],
              "nodeAction": "EXECUTE",
              "inputs": []
            }
        """.trimIndent()
        val decoded = json.decodeFromString(ServerMetaData.serializer(), oldPayload)
        assertFalse(decoded.demoMode)
        assertEquals("example-host", decoded.name)
    }
}
