package krill.zone.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for krill-oss#217: `KrillApp.Swarm` — a new top-level
 * sealed parent (rather than an `Executor.*` child) for `Work` and `Batch`,
 * per the issue's own placement guidance to prefer a new parent when more
 * swarm types are anticipated.
 */
class KrillAppSwarmTest {

    @Test
    fun `Swarm is registered as a top-level KrillApp`() {
        assertTrue(KrillApp.Swarm in (krillAppChildren[null] ?: emptyList()))
    }

    @Test
    fun `Swarm Work and Batch are registered as Swarm children`() {
        assertEquals(
            listOf(KrillApp.Swarm.Work, KrillApp.Swarm.Batch),
            krillAppChildren[KrillApp.Swarm],
        )
    }

    @Test
    fun `Swarm Work and Batch are reachable via allKrillApps`() {
        assertTrue(KrillApp.Swarm.Work in allKrillApps)
        assertTrue(KrillApp.Swarm.Batch in allKrillApps)
    }

    @Test
    fun `lookup resolves Swarm Work by hierarchical and simple name`() {
        assertEquals(KrillApp.Swarm.Work, lookup("Swarm.Work"))
        assertEquals(KrillApp.Swarm.Work, lookup("KrillApp.Swarm.Work"))
        assertEquals(KrillApp.Swarm.Work, lookup("Work"))
    }

    @Test
    fun `lookup resolves Swarm Batch by hierarchical and simple name`() {
        assertEquals(KrillApp.Swarm.Batch, lookup("Swarm.Batch"))
        assertEquals(KrillApp.Swarm.Batch, lookup("KrillApp.Swarm.Batch"))
        assertEquals(KrillApp.Swarm.Batch, lookup("Batch"))
    }
}
