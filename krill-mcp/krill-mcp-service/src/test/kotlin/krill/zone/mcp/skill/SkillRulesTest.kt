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
     * Regression test for issue bsautner/krill-oss#27.
     *
     * The skill was missing a workflow recipe for natural-language
     * action requests ("turn on the Vivarium mister"). Without it,
     * agents in voice flows reasoned every step from primitives,
     * fired wrong-target on close-score candidates, and silently
     * fell back to `record_snapshot` for *execute*-shaped requests.
     * The new section codifies a single resolve → confirm → fire →
     * report chain anchored on `find_node` and the target's
     * `llmSideEffectLevel`.
     */
    @Test
    fun `skill has a natural-language commands workflow recipe`() {
        val text = skillFile.readText()
        assertTrue(
            "natural-language commands" in text || "natural-language command" in text,
            "SKILL.md should include a workflow section for natural-language action " +
                "requests (voice or chat-mode). See issue #27.",
        )
    }

    @Test
    fun `voice-flow recipe spells out the resolve-confirm-fire chain`() {
        val text = skillFile.readText()
        val resolveMentionsFindNode =
            ("Resolve the target" in text) && ("find_node" in text)
        val confirmsOnTargetSideEffect =
            "target's" in text && "llmSideEffectLevel" in text
        val firePrefersExecuteNode = "execute_node" in text
        assertTrue(
            resolveMentionsFindNode,
            "Voice-flow recipe should resolve targets via `find_node` (a single composite " +
                "primitive) — see issue #27 for why scan/filter/match per server isn't a viable " +
                "voice-flow pattern.",
        )
        assertTrue(
            confirmsOnTargetSideEffect,
            "Voice-flow recipe should anchor confirmation on the *target's* `llmSideEffectLevel`, " +
                "not the executor's — a LogicGate is `medium` but the Pin it writes to is `high`.",
        )
        assertTrue(
            firePrefersExecuteNode,
            "Voice-flow recipe should mention `execute_node` (tracked as #24) as the preferred " +
                "fire path, with the `record_snapshot` fallback documented as a stopgap.",
        )
    }

    @Test
    fun `voice-flow recipe warns against the snapshot-as-execute footgun`() {
        val text = skillFile.readText()
        val mentionsSyntheticOrPolluting =
            "synthetic" in text || "pollut" in text
        assertTrue(
            mentionsSyntheticOrPolluting,
            "Voice-flow recipe should call out that `record_snapshot` writes a synthetic reading " +
                "into the source DataPoint's history — agents must surface the trade-off and not " +
                "silently use it as an `execute` substitute. See issue #27.",
        )
    }

    private companion object {
        const val SKILL_PATH = "../skill/krill/SKILL.md"
    }
}
