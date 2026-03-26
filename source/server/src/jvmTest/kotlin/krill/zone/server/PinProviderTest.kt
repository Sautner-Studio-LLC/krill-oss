package krill.zone.server

import kotlin.test.*
import org.junit.jupiter.api.Test

class PinProviderTest {

    @Test
    fun `bearerToken returns null when no file exists`() {
        val provider = PinProvider()
        // Default path won't exist in test env
        assertNull(provider.bearerToken())
    }

    @Test
    fun `isConfigured returns false when no PIN exists`() {
        val provider = PinProvider()
        assertFalse(provider.isConfigured())
    }

    @Test
    fun `validateBearer returns false when no PIN configured`() {
        val provider = PinProvider()
        assertFalse(provider.validateBearer("any-token"))
    }

    @Test
    fun `validateBearer accepts matching token`() {
        val provider = TestPinProvider("abc123def456")
        assertTrue(provider.validateBearer("abc123def456"))
    }

    @Test
    fun `validateBearer rejects mismatched token`() {
        val provider = TestPinProvider("correct-token")
        assertFalse(provider.validateBearer("wrong-token"))
    }

    @Test
    fun `validateBearer rejects different length`() {
        val provider = TestPinProvider("short")
        assertFalse(provider.validateBearer("much-longer-token"))
    }

    @Test
    fun `constantTimeEquals works correctly`() {
        assertTrue(PinProvider.constantTimeEquals("hello", "hello"))
        assertFalse(PinProvider.constantTimeEquals("hello", "world"))
        assertFalse(PinProvider.constantTimeEquals("abc", "abcd"))
    }

    private class TestPinProvider(private val fixedToken: String?) : PinProvider() {
        override fun bearerToken(): String? = fixedToken
    }
}
