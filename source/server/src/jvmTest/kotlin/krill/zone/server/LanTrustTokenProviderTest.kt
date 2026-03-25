package krill.zone.server

import kotlin.test.*
import org.junit.jupiter.api.Test

class LanTrustTokenProviderTest {

    @Test
    fun `token returns null when file does not exist`() {
        val provider = LanTrustTokenProvider()
        // Default path won't exist in test env
        assertNull(provider.token())
    }

    @Test
    fun `validate returns false when no token configured`() {
        val provider = LanTrustTokenProvider()
        assertFalse(provider.validate("any-token"))
    }

    @Test
    fun `validate accepts matching token`() {
        val provider = TestLanTrustTokenProvider("test-token-123")
        assertTrue(provider.validate("test-token-123"))
    }

    @Test
    fun `validate rejects mismatched token`() {
        val provider = TestLanTrustTokenProvider("correct-token")
        assertFalse(provider.validate("wrong-token"))
    }

    @Test
    fun `validate rejects empty candidate`() {
        val provider = TestLanTrustTokenProvider("some-token")
        assertFalse(provider.validate(""))
    }

    @Test
    fun `validate rejects when lengths differ`() {
        val provider = TestLanTrustTokenProvider("short")
        assertFalse(provider.validate("much-longer-token"))
    }

    private class TestLanTrustTokenProvider(private val fixedToken: String?) : LanTrustTokenProvider() {
        override fun token(): String? = fixedToken
    }
}
