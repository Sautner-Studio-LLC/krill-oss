package krill.zone.mcp.skill

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue Sautner-Studio-LLC/krill-oss#12.
 *
 * The skill's "build this tree" section used to declare a hard rule that
 * `KrillApp.DataPoint` must be parented by `KrillApp.Server` — `**Not a
 * Project.**` — even though the Krill server accepts other parents. An
 * agent following that rule verbatim would refuse to mirror an existing
 * project-organised tree (see `pi-krill-05`).
 *
 * This test guards against a future re-introduction of the absolute, and
 * ensures the relaxed wording continues to mention that the server is
 * permissive about parent types.
 */
class SkillRulesTest {

    private val skillFile = File(SKILL_PATH)

    @Test
    fun `skill exists at the expected path`() {
        assertTrue(skillFile.exists(), "Expected skill at $SKILL_PATH")
    }

    @Test
    fun `skill no longer asserts DataPoint parent must not be a Project`() {
        val text = skillFile.readText()
        assertFalse(
            "**Not a Project.**" in text,
            "SKILL.md still contains the absolute rule '**Not a Project.**' — the Krill server " +
                "accepts DataPoints under Projects (and under other DataPoints). See issue #12.",
        )
    }

    @Test
    fun `skill calls out that the catalog parents are not server-enforced`() {
        val text = skillFile.readText()
        assertTrue(
            "permissive" in text || "not server-enforced" in text,
            "SKILL.md should explicitly note that the catalog's parent types are typical / not " +
                "server-enforced, so agents don't 'correct' a working tree that places DataPoints " +
                "under Projects or other DataPoints.",
        )
    }

    private companion object {
        const val SKILL_PATH = "../skill/krill/SKILL.md"
    }
}
