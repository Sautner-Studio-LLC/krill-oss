package krill.zone.mcp.skill

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regression test for issue Sautner-Studio-LLC/krill-oss#38.
 *
 * Both repos moved from the old user-prefixed org to `Sautner-Studio-LLC`
 * on 2026-04-30. Stale slugs that survive the rename are not just
 * cosmetic — `release-sdk.yml` dispatches by repo slug and silently
 * no-ops if the slug is wrong (see lesson #17), and POM URLs baked into
 * a `krill-pi4j` Maven artifact would lock stale SCM into Central for
 * the lifetime of that version.
 *
 * Greps every tracked text file under the repo root for the concrete
 * old-org slug (constructed at runtime so this source file isn't a
 * self-match). Standalone occurrences of the legacy username (used in
 * `developer.id` POM blocks) and asterisk-suffixed wildcard mentions
 * (historical narrative in older lessons) are intentionally allowed.
 */
class OrgReferencesTest {

    @Test
    fun `no stale old-org repo slug survives in tracked text files`() {
        val repoRoot = File(REPO_ROOT_RELATIVE).canonicalFile
        assertTrue(repoRoot.exists(), "Expected repo root at ${repoRoot.path}")

        val pendingPaths = PENDING_PATHS.map { File(repoRoot, it).canonicalPath }.toSet()
        val offenders = repoRoot.walkTopDown()
            .onEnter { dir -> dir.name !in PRUNE_DIRS }
            .filter { it.isFile && it.extension.lowercase() in TEXT_EXTENSIONS }
            .filter { it.canonicalPath != selfPath }
            .filter { it.canonicalPath !in pendingPaths }
            .flatMap { file ->
                file.useLines { lines ->
                    lines.mapIndexedNotNull { idx, line ->
                        if (FORBIDDEN_REGEX.containsMatchIn(line)) {
                            "${file.relativeTo(repoRoot).path}:${idx + 1}: $line"
                        } else {
                            null
                        }
                    }.toList()
                }
            }
            .toList()

        if (offenders.isNotEmpty()) {
            fail(
                "Found ${offenders.size} stale old-org slug(s) — " +
                    "the rename to Sautner-Studio-LLC must replace them. See issue #38.\n" +
                    offenders.joinToString("\n"),
            )
        }
    }

    private val selfPath: String = File(
        "src/test/kotlin/krill/zone/mcp/skill/OrgReferencesTest.kt",
    ).canonicalPath

    private companion object {
        // Resolved relative to the krill-mcp-service Gradle working dir
        // (../.. lands at the krill-oss repo root).
        const val REPO_ROOT_RELATIVE = "../.."

        // Built at runtime so this source file doesn't itself contain
        // the forbidden substring. Matches the concrete `<old-org>/krill`
        // and `<old-org>/krill-oss` slugs but skips the asterisk-suffixed
        // wildcard form used as historical narrative in lessons.
        private const val OLD_ORG = "bsautner"
        val FORBIDDEN_REGEX = Regex("""$OLD_ORG/k""")

        val PRUNE_DIRS = setOf(
            ".git", ".gradle", ".kotlin", ".idea",
            "build", "node_modules",
        )

        // Files whose old-org slugs require a follow-up PR Ben pushes
        // himself: the krill-blue-bot PAT lacks `workflow` scope, so
        // GitHub's webhook rejects any push that touches these paths.
        // Tracked in the issue linked from the PR for #38; remove from
        // this set once that PR lands.
        val PENDING_PATHS = setOf(
            ".github/workflows/release-sdk.yml",
        )

        val TEXT_EXTENSIONS = setOf(
            "md", "kt", "kts", "yml", "yaml", "json",
            "toml", "gradle", "properties", "txt", "sh",
            "py", "xml", "svg",
        )
    }
}
