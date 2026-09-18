package krill.zone.service

import com.krillforge.pi4j.proto.spiDeviceId
import com.krillforge.pi4j.proto.spiReadRequest
import com.krillforge.pi4j.proto.spiWriteRequest
import com.google.protobuf.ByteString
import com.pi4j.Pi4J
import com.pi4j.plugin.mock.provider.spi.MockSpiProviderImpl
import kotlinx.coroutines.runBlocking
import krill.zone.Pi4jContextManager
import krill.zone.Pi4jContextManager.ProviderFamily
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [DefaultSpiService] against the mock SPI provider (no real hardware) —
 * regression coverage for krill-oss#250.
 */
class DefaultSpiServiceTest {

    @AfterTest
    fun tearDown() {
        Pi4jContextManager.shutdown()
    }

    private fun initMockContext() {
        val context = Pi4J.newContextBuilder().add(MockSpiProviderImpl()).build()
        Pi4jContextManager.initializeForTest(context, ProviderFamily.MOCK)
    }

    @Test
    fun `write then read round-trips the same bytes through the mock provider`() = runBlocking {
        initMockContext()
        val service = DefaultSpiService(Pi4jContextManager)
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val writeResult = service.write(spiWriteRequest {
            device = spiDeviceId { bus = 0; chipSelect = 0 }
            data = ByteString.copyFrom(payload)
        })
        assertTrue(writeResult.success, "write failed: ${writeResult.message}")

        val readResult = service.read(spiReadRequest {
            device = spiDeviceId { bus = 0; chipSelect = 0 }
            length = payload.size
        })
        assertTrue(readResult.success, "read failed: ${readResult.message}")
        assertEquals(payload.toList(), readResult.readData.toByteArray().toList())
    }

    @Test
    fun `devices are cached by (bus, chipSelect)`() = runBlocking {
        initMockContext()
        val service = DefaultSpiService(Pi4jContextManager)

        // Same (bus, chipSelect) pair reused across calls must not error on reopen.
        repeat(2) {
            val result = service.write(spiWriteRequest {
                device = spiDeviceId { bus = 1; chipSelect = 2 }
                data = ByteString.copyFrom(byteArrayOf(0x2A))
            })
            assertTrue(result.success, "write failed on iteration $it: ${result.message}")
        }
    }
}
