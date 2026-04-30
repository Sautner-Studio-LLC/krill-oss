package krill.zone.mcp.skill

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for issue Sautner-Studio-LLC/krill-oss#36.
 *
 * The QA agent's CLAUDE.md previously said "Do not make code changes",
 * which the QA bot interpreted narrowly enough to edit its own
 * instructions on 2026-04-30 (lost on VM rebuild). Filings also went
 * out unassigned, leaving issues in nobody's queue. Issue #36 tightens
 * the role wording, adds an always-assign-to-krill-blue-bot rule with
 * concrete recipe edits, and appends a Hard rules section.
 */
class QaRulesTest {

    private val qaFile = File(QA_CLAUDE_PATH)

    @Test
    fun `qa CLAUDE_md exists at the expected path`() {
        assertTrue(qaFile.exists(), "Expected krill-qa CLAUDE.md at $QA_CLAUDE_PATH")
    }

    @Test
    fun `qa CLAUDE_md no longer says only 'Do not make code changes'`() {
        val text = qaFile.readText()
        assertFalse(
            "Do not make code changes" in text,
            "krill-qa/CLAUDE.md still uses the loose 'Do not make code changes' wording. " +
                "Per issue #36 it should say 'Do not edit any file' (broader, unambiguous).",
        )
    }

    @Test
    fun `qa CLAUDE_md uses the broader 'Do not edit any file' wording`() {
        val text = qaFile.readText()
        assertTrue(
            "Do not edit any file" in text,
            "krill-qa/CLAUDE.md should explicitly forbid file edits, not just code changes. See issue #36.",
        )
    }

    @Test
    fun `qa CLAUDE_md instructs the QA agent to assign every issue to krill-blue-bot`() {
        val text = qaFile.readText()
        assertTrue(
            "krill-blue-bot" in text,
            "krill-qa/CLAUDE.md must mention 'krill-blue-bot' as the always-assignee. See issue #36.",
        )
        assertTrue(
            "--assignee krill-blue-bot" in text,
            "Both gh issue create examples in krill-qa/CLAUDE.md must include " +
                "'--assignee krill-blue-bot' so the QA bot copies the flag verbatim. See issue #36.",
        )
    }

    @Test
    fun `qa CLAUDE_md has a Hard rules section`() {
        val text = qaFile.readText()
        assertTrue(
            "Hard rules" in text,
            "krill-qa/CLAUDE.md must end with a 'Hard rules' section enumerating " +
                "what the QA agent must never do (file edits, branching, PRs, mis-assignment, " +
                "umbrella-label misuse). See issue #36.",
        )
    }

    private companion object {
        // Resolved relative to the krill-mcp-service Gradle working dir
        // (../.. lands at the krill-oss repo root).
        const val QA_CLAUDE_PATH = "../../krill-qa/CLAUDE.md"
    }
}
