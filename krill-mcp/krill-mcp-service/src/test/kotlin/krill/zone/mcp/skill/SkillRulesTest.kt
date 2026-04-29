package krill.zone.mcp.skill

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue bsautner/krill-oss#12.
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

    /**
     * Regression tests for issue bsautner/krill-oss#25.
     *
     * QA hit two gaps in a multi-server swarm session:
     *
     * (A) The skill had no recipe for *manually firing* a Trigger / Executor /
     *     LogicGate (the "fire this gate once" intent equivalent to the in-app
     *     manual-execute button). The agent had to rediscover the
     *     `record_snapshot`-upstream pattern from first principles.
     *
     * (B) When two Krill servers in the same swarm both host a project named
     *     "Vivarium", `list_projects` can return matches from any server and
     *     the skill's "Pick the parent project" guidance silently falls
     *     through to "if exactly one, use it" — which is wrong, because the
     *     identity is `(serverId, projectId)`, not name.
     *
     * These assertions guard the two SKILL.md additions that close those
     * gaps. They check phrasing, not exact wording, so reasonable rewording
     * of the additions won't break the tests; the additions disappearing
     * entirely will.
     */

    @Test
    fun `skill documents how to fire a Trigger or Executor manually`() {
        val text = skillFile.readText()
        assertTrue(
            Regex("""(?i)fire a (Trigger or Executor|gate) manually""").containsMatchIn(text),
            "SKILL.md should have a top-level workflow for manually firing a Trigger / Executor — " +
                "the in-app manual-execute equivalent. See issue #25.",
        )
    }

    @Test
    fun `skill describes the record_snapshot upstream stopgap for manual firing`() {
        val text = skillFile.readText()
        assertTrue(
            "meta.sources" in text && "record_snapshot" in text,
            "SKILL.md should document the `record_snapshot`-upstream stopgap for firing a gate " +
                "(get_node → meta.sources[0].nodeId, record_snapshot to flip the evaluation). " +
                "See issue #25.",
        )
    }

    @Test
    fun `skill flags the record_snapshot upstream pattern as a stopgap not a canonical answer`() {
        val text = skillFile.readText()
        assertTrue(
            Regex("""(?i)(stopgap|pollutes|until `?execute_node`?)""").containsMatchIn(text),
            "SKILL.md should explicitly frame the record_snapshot-upstream firing pattern as a " +
                "stopgap (it pollutes the upstream DataPoint history) rather than the canonical " +
                "answer — so a future agent doesn't blindly follow it. See issue #25.",
        )
    }

    @Test
    fun `skill warns that project names are not swarm-unique`() {
        val text = skillFile.readText()
        assertTrue(
            Regex("""(?i)project names? (are )?not swarm[- ]unique""").containsMatchIn(text),
            "SKILL.md should warn that project names are not swarm-unique — two servers can each " +
                "host a project named the same thing. See issue #25.",
        )
    }

    @Test
    fun `skill names the serverId projectId tuple as project identity`() {
        val text = skillFile.readText()
        assertTrue(
            Regex("""\(\s*serverId\s*,\s*projectId\s*\)""").containsMatchIn(text),
            "SKILL.md should call out `(serverId, projectId)` as the project identity, not the " +
                "name, so agents disambiguate explicitly across peers. See issue #25.",
        )
    }

    @Test
    fun `skill tells the agent to ask which server when list_projects returns duplicates across peers`() {
        val text = skillFile.readText()
        assertTrue(
            Regex(
                """(?is)which server(['']s)?\b""",
            ).containsMatchIn(text),
            "SKILL.md should instruct the agent to ask the user which **server's** project to " +
                "use when `list_projects` returns same-named matches from different servers — not " +
                "silently pick the first or merge them. See issue #25.",
        )
    }

    private companion object {
        const val SKILL_PATH = "../skill/krill/SKILL.md"
    }
}
