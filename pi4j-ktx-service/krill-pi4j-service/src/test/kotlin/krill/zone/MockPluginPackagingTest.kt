package krill.zone

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression guard for Sautner-Studio-LLC/krill-oss#264.
 *
 * `pi4j-plugin-mock` used to be a `testImplementation`-only dependency, so
 * `PI4J_MOCK=true`/`--mock` only worked under the Gradle test task — the installed
 * daemon (shadowJar) never had the mock platform on its classpath. On hosts with no
 * `spi`/`gpio` OS groups (e.g. the ghost QA runner), the FFM plugin's hardware providers
 * fail to initialize and there was no way to bring the daemon up at all. This fails if
 * the dependency ever regresses back to `testImplementation`.
 */
class MockPluginPackagingTest {

    @Test
    fun `pi4j-plugin-mock is a real implementation dependency, not test-only`() {
        val buildFile = File("build.gradle.kts")
        assertTrue(buildFile.exists(), "Expected file not found at ${buildFile.canonicalPath}")
        val contents = buildFile.readText()

        assertTrue(
            contents.contains("implementation(libs.pi4j.plugin.mock)"),
            "pi4j-plugin-mock must be a real `implementation` dependency so it ships in the shadowJar",
        )
        assertFalse(
            contents.contains("testImplementation(libs.pi4j.plugin.mock)"),
            "pi4j-plugin-mock must not regress back to a testImplementation-only dependency (krill-oss#264)",
        )
    }

    @Test
    fun `postinst defaults PI4J_MOCK to true when neither gpio nor spi groups exist`() {
        val postinst = File("package/DEBIAN/postinst")
        assertTrue(postinst.exists(), "Expected file not found at ${postinst.canonicalPath}")
        val contents = postinst.readText()

        assertTrue(
            contents.contains("""if getent group gpio >/dev/null || getent group spi >/dev/null; then"""),
            "postinst must probe for gpio/spi groups to decide the PI4J_MOCK default (krill-oss#264)",
        )
        assertTrue(
            contents.contains("""Environment=PI4J_MOCK=${'$'}{PI4J_MOCK_DEFAULT}"""),
            "the generated systemd unit must use the computed PI4J_MOCK_DEFAULT, not a hardcoded false",
        )
        assertFalse(
            contents.contains("Environment=PI4J_MOCK=false"),
            "PI4J_MOCK must not regress back to an unconditional false — that reintroduces krill-oss#264 " +
                "on hosts with no real gpio/spi groups",
        )
    }
}
