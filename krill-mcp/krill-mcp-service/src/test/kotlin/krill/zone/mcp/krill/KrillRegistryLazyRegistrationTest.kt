package krill.zone.mcp.krill

import krill.zone.mcp.auth.PinProvider
import krill.zone.mcp.config.KrillMcpConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for issue bsautner/krill-oss#23 (Part B — lazy host
 * registration).
 *
 * Today, a tool call passing `server: "pi-krill.local"` errors with
 * `"No Krill server matches 'pi-krill.local' (and no default is registered)."`
 * when that host isn't in the seed config. The wording wrongly implies the
 * host doesn't exist; the real problem is that krill-mcp's registry is
 * seed-config-driven and bootstrap-only.
 *
 * `KrillRegistry.tryRegisterByHost(selector)` lets an agent address a peer
 * by hostname without needing shell access to the krill-mcp host, edits to
 * `/etc/krill-mcp/config.json`, and a `systemctl restart`.
 *
 * Note: Part A (transitive autodiscovery from one seed) depends on the krill
 * server exposing a peer list via `/health` — relayed upstream as
 * bsautner/krill#178. Once that lands, `bootstrap()`/`reseed()` can recurse
 * over peers and the lazy path becomes a fallback rather than the primary
 * way to reach a peer.
 */
class KrillRegistryLazyRegistrationTest {

    private fun emptyRegistry() = KrillRegistry(
        config = KrillMcpConfig(),
        pin = PinProvider(path = "/dev/null"),
    )

    @Test
    fun `looksLikeHost recognises FQDNs and IPs by their dot`() {
        assertTrue(KrillRegistry.looksLikeHost("pi-krill.local"))
        assertTrue(KrillRegistry.looksLikeHost("pi-krill.local:8442"))
        assertTrue(KrillRegistry.looksLikeHost("192.168.1.10"))
        assertTrue(KrillRegistry.looksLikeHost("192.168.1.10:8442"))
    }

    @Test
    fun `looksLikeHost recognises explicit host colon port form`() {
        assertTrue(KrillRegistry.looksLikeHost("pi-krill:8442"))
    }

    @Test
    fun `looksLikeHost rejects blanks and UUIDs to avoid pointless DNS lookups`() {
        // Blank / null
        assertFalse(KrillRegistry.looksLikeHost(null))
        assertFalse(KrillRegistry.looksLikeHost(""))
        assertFalse(KrillRegistry.looksLikeHost("   "))
        // Bare name without dot or port — keeps the registry honest about
        // what was probed vs what was randomly typed at it.
        assertFalse(KrillRegistry.looksLikeHost("pi-krill"))
        // UUID-ish — matches /^[a-z0-9-]+$/ but a DNS lookup on this would
        // burn 5s of connect timeout for no reason.
        assertFalse(KrillRegistry.looksLikeHost("f47ac10b-58cc-4372-a567-0e02b2c3d479"))
    }

    @Test
    fun `tryRegisterByHost returns null for non-host selectors without touching the network`() {
        val registry = emptyRegistry()
        // No PIN-derived bearer either, so a network attempt would also fail —
        // but the predicate short-circuits before even getting there.
        val result = runBlocking { registry.tryRegisterByHost("not-a-host") }
        assertNull(result)
        assertTrue(registry.all().isEmpty(), "registry should stay empty when no host was probed")
    }

    @Test
    fun `tryRegisterByHost returns null for an unreachable host and leaves the registry untouched`() {
        // RFC 2606 reserves '.invalid' for guaranteed-NXDOMAIN — DNS fails
        // fast so this test doesn't pay the full 5s connect timeout.
        val registry = emptyRegistry()
        val result = runBlocking { registry.tryRegisterByHost("krill-mcp-issue-23.invalid") }
        assertNull(result)
        assertTrue(registry.all().isEmpty(), "unreachable host must not be registered")
    }
}
