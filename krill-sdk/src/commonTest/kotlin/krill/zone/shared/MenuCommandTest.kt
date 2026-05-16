package krill.zone.shared

import krill.zone.shared.node.isMenuOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for issue Sautner-Studio-LLC/krill-oss#67.
 *
 * `MenuCommand.KeepBuildingSwarm` is the entry into the FTUE walkthrough
 * chooser when the user clicks a `KrillApp.Client` node downstream. This
 * test pins three things the consuming Compose app relies on:
 *
 *  1. The `data object` exists and is reachable as a singleton — equality
 *     by identity, no constructor parameters.
 *  2. It routes through the `is MenuCommand` discriminator that
 *     `KrillApp.isMenuOption()` and `NodeChildren` use to skip
 *     menu-command discriminators in real-node iteration.
 *  3. `simpleName` is exactly `"KeepBuildingSwarm"` — the polymorphic
 *     wire form is the discriminator string, so the symbol name *is*
 *     the API.
 */
class MenuCommandTest {

    @Test
    fun `KeepBuildingSwarm is a MenuCommand`() {
        val cmd: KrillApp = MenuCommand.KeepBuildingSwarm
        assertTrue(cmd is MenuCommand)
        assertTrue(cmd.isMenuOption())
    }

    @Test
    fun `KeepBuildingSwarm singleton identity holds`() {
        assertEquals(MenuCommand.KeepBuildingSwarm, MenuCommand.KeepBuildingSwarm)
    }

    @Test
    fun `KeepBuildingSwarm simpleName matches downstream wire contract`() {
        assertEquals("KeepBuildingSwarm", MenuCommand.KeepBuildingSwarm::class.simpleName)
    }
}
