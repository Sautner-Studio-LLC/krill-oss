package krill.zone.shared.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Golden-vector regression tests for [PinDerivation].
 *
 * Every platform target (JVM, Android, iOS, wasmJs) must produce these exact
 * hex strings for identical inputs — that is the byte-identical contract of the
 * expect/actual split.  A failure here means one platform's crypto backend has
 * drifted from the others; any token it produces will be rejected by every
 * server and peer on every other platform.
 *
 * The expected values were computed with:
 *   `HMAC-SHA256(key="krill-api-pbkdf2-v1", data="123456")` → bearer
 *   `HMAC-SHA256(key=nodeUuid, data=pin)` then `HMAC-SHA256(key=nodeKey, data=window)`
 *     → first 4 bytes → beacon
 * and verified against the Python `hmac` / `hashlib` standard library.
 */
class PinDerivationTest {

    @Test
    fun `deriveBearerToken golden vector for pin 123456`() {
        assertEquals(
            "2d0f4836009a8c9d762995a16bad886ef77619c6c946cb52c798d389cc6cba97",
            PinDerivation.deriveBearerToken("123456")
        )
    }

    @Test
    fun `deriveBearerToken always returns 64-character lowercase hex`() {
        val result = PinDerivation.deriveBearerToken("1234")
        assertEquals(64, result.length, "Bearer token must be 64 hex chars (32 bytes)")
        assertTrue(result.all { it in '0'..'9' || it in 'a'..'f' }, "Bearer token must be lowercase hex")
    }

    @Test
    fun `deriveBearerToken is deterministic for the same pin`() {
        assertEquals(
            PinDerivation.deriveBearerToken("123456"),
            PinDerivation.deriveBearerToken("123456")
        )
    }

    @Test
    fun `deriveBearerToken differs for different pins`() {
        assertNotEquals(
            PinDerivation.deriveBearerToken("123456"),
            PinDerivation.deriveBearerToken("654321")
        )
    }

    @Test
    fun `deriveBeaconToken golden vector for fixed inputs`() {
        assertEquals(
            "c6aacfef",
            PinDerivation.deriveBeaconToken(
                pin = "123456",
                nodeUuid = "550e8400-e29b-41d4-a716-446655440000",
                epochSeconds = 1700000000L
            )
        )
    }

    @Test
    fun `deriveBeaconToken always returns 8-character lowercase hex`() {
        val result = PinDerivation.deriveBeaconToken("1234", "any-uuid", 0L)
        assertEquals(8, result.length, "Beacon token must be 8 hex chars (4 bytes)")
        assertTrue(result.all { it in '0'..'9' || it in 'a'..'f' }, "Beacon token must be lowercase hex")
    }

    @Test
    fun `deriveBeaconToken is stable within a 30-second window`() {
        // 1700000010 is the start of window 56666667 (56666667 * 30 = 1700000010).
        val base = 1700000010L
        val atWindowStart = PinDerivation.deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-446655440000", base)
        val atWindowEnd = PinDerivation.deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-446655440000", base + 29L)
        assertEquals(atWindowStart, atWindowEnd, "Beacon must be stable across the same 30-second window")
    }

    @Test
    fun `deriveBeaconToken rotates at the 30-second boundary`() {
        val base = 1700000010L
        val thisWindow = PinDerivation.deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-446655440000", base)
        val nextWindow = PinDerivation.deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-446655440000", base + 30L)
        assertNotEquals(thisWindow, nextWindow, "Beacon must rotate when the 30-second window advances")
    }

    @Test
    fun `deriveBeaconToken differs for different node UUIDs`() {
        val token1 = PinDerivation.deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-446655440000", 1700000000L)
        val token2 = PinDerivation.deriveBeaconToken("123456", "550e8400-e29b-41d4-a716-000000000001", 1700000000L)
        assertNotEquals(token1, token2, "Beacon tokens for different UUIDs must differ")
    }
}
