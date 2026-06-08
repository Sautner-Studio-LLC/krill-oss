# grpc-netty-shaded GHSA propagates to krill server via krill-pi4j artifact

**Issue:** [krill-oss#127](https://github.com/Sautner-Studio-LLC/krill-oss/issues/127)
**Root cause category:** Dependency hygiene — shaded CVE in a published library bleeds onto all consumers
**Module:** `krill-pi4j`

## What happened

`krill-pi4j 0.0.3` declared `io.grpc:grpc-netty-shaded:1.68.1` as an
`implementation` dependency in its published POM. The shaded jar bundles
Netty internally; a CVE in that bundled Netty version was flagged high by
Dependabot on the `krill` server repo, which depends on krill-pi4j. Because
`grpc-netty-shaded` is a fat/shaded JAR, consumers **cannot** override its
internal Netty version via a normal dependency constraint — the only fix is
to bump gRPC in `krill-pi4j` itself and republish.

## Fix

- `pi4j-ktx-service/gradle/libs.versions.toml`: bumped `grpc = "1.68.1"` → `"1.75.0"`.
- `pi4j-ktx-service/krill-pi4j/src/test/kotlin/com/krillforge/pi4j/GrpcVersionGuardTest.kt`:
  added a regression test that reads the version catalog and fails if the
  `grpc` key ever falls below 1.75.0 again.

## Prevention

- **Treat any `*-shaded` or `*-all` dependency bump as equivalent to a direct
  Netty/Guava/etc bump.** Consumers cannot override shaded coordinates, so
  even a high-severity CVE in a shaded transitive is a blocker for the
  library, not just the consumer.
- **Add a version-floor test to every module that ships a shaded transport
  JAR.** A test that reads `libs.versions.toml` and asserts a minimum version
  gives both the test suite and reviewers a visible guard against regressions.
- **When a security advisory names a minimum safe version, go directly to
  that version (or higher) rather than to the next patch.** Going from
  1.68.1 to 1.68.3 would not have cleared the GHSA.
