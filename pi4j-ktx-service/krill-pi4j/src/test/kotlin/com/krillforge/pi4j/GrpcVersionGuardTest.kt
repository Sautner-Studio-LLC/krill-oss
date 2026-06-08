package com.krillforge.pi4j

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression guard for Sautner-Studio-LLC/krill-oss#127.
 *
 * grpc-netty-shaded < 1.75.0 ships a bundled Netty CVE (GHSA) that propagates
 * onto any downstream consumer (including the krill server) via the published
 * krill-pi4j Maven artifact. Shaded coordinates cannot be overridden by
 * consumers — the only fix is a version bump here.
 *
 * Reads the version catalog and fails if someone reverts the bump below 1.75.0.
 */
class GrpcVersionGuardTest {

    @Test
    fun `grpc version in libs-versions-toml is at least 1_75_0`() {
        val toml = File("../gradle/libs.versions.toml")
        assertTrue(toml.exists(), "Version catalog not found at ${toml.canonicalPath}")

        val match = Regex("""^grpc\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(toml.readText())
            ?: error("grpc version key not found in libs.versions.toml")
        val version = match.groupValues[1]

        val parts = version.split(".")
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        assertTrue(
            major > 1 || (major == 1 && minor >= 75),
            "grpc must be >= 1.75.0 in libs.versions.toml to clear GHSA (krill-oss#127); found $version",
        )
    }
}
