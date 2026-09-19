package krill.zone

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression guard for Sautner-Studio-LLC/krill-oss#249.
 *
 * pi4j-ktx-service used to carry four independently hand-maintained version
 * numbers (krill-pi4j/build.gradle.kts, krill-pi4j-service/build.gradle.kts,
 * Version.kt, package/DEBIAN/control) that silently drifted apart — the
 * deployed daemon reported 0.0.5 while the .deb that installed it declared
 * 0.0.4. `gradle.properties`' `pi4jVersion` is now the single source of
 * truth; both build files derive `version` from it. This test fails if
 * Version.kt or DEBIAN/control are ever hand-edited out of sync with it
 * again, or if either build file reverts to a hardcoded literal.
 */
class VersionConsistencyTest {

    private fun readFile(path: String): String {
        val file = File(path)
        assertTrue(file.exists(), "Expected file not found at ${file.canonicalPath}")
        return file.readText()
    }

    private fun singleLineMatch(path: String, regex: Regex): String =
        regex.find(readFile(path))?.groupValues?.get(1)
            ?: error("Pattern ${regex.pattern} not found in $path")

    @Test
    fun `pi4jVersion is the single source of truth for both modules, Version_kt, and DEBIAN control`() {
        val sourceOfTruth = singleLineMatch(
            "../gradle.properties",
            Regex("""^pi4jVersion\s*=\s*(\S+)""", RegexOption.MULTILINE),
        )

        val clientBuildFile = readFile("../krill-pi4j/build.gradle.kts")
        assertTrue(
            clientBuildFile.contains("""version = property("pi4jVersion") as String"""),
            "krill-pi4j/build.gradle.kts must derive `version` from the pi4jVersion gradle property, " +
                "not a hardcoded literal",
        )

        val serviceBuildFile = readFile("build.gradle.kts")
        assertTrue(
            serviceBuildFile.contains("""version = property("pi4jVersion") as String"""),
            "krill-pi4j-service/build.gradle.kts must derive `version` from the pi4jVersion gradle property, " +
                "not a hardcoded literal",
        )

        val serviceConstant = singleLineMatch(
            "src/main/kotlin/krill/zone/Version.kt",
            Regex("""const val SERVICE\s*=\s*"([^"]+)""""),
        )
        assertEquals(
            sourceOfTruth,
            serviceConstant,
            "Version.kt's SERVICE constant has drifted from gradle.properties' pi4jVersion",
        )

        val controlVersion = singleLineMatch(
            "package/DEBIAN/control",
            Regex("""^Version:\s*(\S+)""", RegexOption.MULTILINE),
        )
        assertEquals(
            sourceOfTruth,
            controlVersion,
            "package/DEBIAN/control's Version has drifted from gradle.properties' pi4jVersion",
        )
    }
}
