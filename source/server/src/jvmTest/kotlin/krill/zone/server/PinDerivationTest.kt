package krill.zone.server

import kotlin.test.*
import krill.zone.shared.security.*
import org.junit.jupiter.api.Test

class PinDerivationTest {

    @Test
    fun `deriveBearerToken produces consistent output for same PIN`() {
        val token1 = PinDerivation.deriveBearerToken("1234")
        val token2 = PinDerivation.deriveBearerToken("1234")
        assertEquals(token1, token2)
    }

    @Test
    fun `deriveBearerToken produces different output for different PINs`() {
        val token1 = PinDerivation.deriveBearerToken("1234")
        val token2 = PinDerivation.deriveBearerToken("5678")
        assertNotEquals(token1, token2)
    }

    @Test
    fun `deriveBearerToken produces 64-char hex string`() {
        val token = PinDerivation.deriveBearerToken("0000")
        assertEquals(64, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `deriveBeaconToken produces 8-char hex string`() {
        val token = PinDerivation.deriveBeaconToken("1234", "test-uuid", 1700000000L)
        assertEquals(8, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `deriveBeaconToken changes with different time windows`() {
        val t1 = PinDerivation.deriveBeaconToken("1234", "test-uuid", 1700000000L)
        val t2 = PinDerivation.deriveBeaconToken("1234", "test-uuid", 1700000030L) // next window
        assertNotEquals(t1, t2)
    }

    @Test
    fun `deriveBeaconToken same within time window`() {
        // Both in the same 30-second window (floor(t/30) is equal)
        val base = 1700000010L // floor(1700000010/30) = 56666667
        val t1 = PinDerivation.deriveBeaconToken("1234", "test-uuid", base)
        val t2 = PinDerivation.deriveBeaconToken("1234", "test-uuid", base + 10L) // same window
        assertEquals(t1, t2)
    }

    @Test
    fun `deriveBeaconToken differs for different node UUIDs`() {
        val t1 = PinDerivation.deriveBeaconToken("1234", "uuid-a", 1700000000L)
        val t2 = PinDerivation.deriveBeaconToken("1234", "uuid-b", 1700000000L)
        assertNotEquals(t1, t2)
    }
}
