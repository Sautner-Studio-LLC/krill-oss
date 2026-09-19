package krill.zone

import com.krillforge.pi4j.proto.i2cDeviceId
import com.krillforge.pi4j.proto.i2cRegisterRequest
import com.krillforge.pi4j.proto.pwmConfig
import com.krillforge.pi4j.proto.setOutputRequest
import com.krillforge.pi4j.proto.spiDeviceId
import com.krillforge.pi4j.proto.spiReadRequest
import com.pi4j.Pi4J
import com.pi4j.io.IOType
import com.pi4j.plugin.raspberrypi.platform.RaspberryPiPlatform
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import krill.zone.Pi4jContextManager.ProviderFamily
import krill.zone.service.DefaultI2cService
import krill.zone.service.DefaultPwmService
import krill.zone.service.DefaultSpiService
import krill.zone.service.GpioServiceImpl
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regression test for krill-oss#244: with only the `raspberrypi` platform registered
 * (no FFM providers on the classpath/host — the exact shape that silently lied for
 * months), every guarded IOType must be marked degraded, and every RPC that would
 * otherwise touch a stub provider must fail with `FAILED_PRECONDITION` instead of
 * returning `success = true`.
 */
class StubProviderGuardTest {

    @AfterTest
    fun tearDown() {
        Pi4jContextManager.shutdown()
    }

    private fun initRaspberryPiOnlyContext() {
        val context = Pi4J.newContextBuilder().add(RaspberryPiPlatform()).build()
        Pi4jContextManager.initializeForTest(context, ProviderFamily.FFM)
    }

    @Test
    fun `raspberrypi-only context marks every guarded IOType degraded`() {
        initRaspberryPiOnlyContext()

        for (type in listOf(IOType.DIGITAL_INPUT, IOType.DIGITAL_OUTPUT, IOType.PWM, IOType.I2C, IOType.SPI)) {
            assertTrue(Pi4jContextManager.isDegraded(type), "$type must be degraded with no FFM providers registered")
        }
    }

    @Test
    fun `PwmService Configure fails with FAILED_PRECONDITION instead of success=true`() {
        initRaspberryPiOnlyContext()
        val service = DefaultPwmService(Pi4jContextManager)

        val request = pwmConfig {
            pin = 18
            frequency = 1000
            dutyCycle = 50f
        }

        val error = runCatching { runBlocking { service.configure(request) } }.exceptionOrNull()
            ?: fail("expected configure() to throw instead of returning a response")

        assertTrue(error is StatusException, "expected a StatusException, got $error")
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

    @Test
    fun `I2cService ReadRegister fails with FAILED_PRECONDITION instead of success=true`() {
        initRaspberryPiOnlyContext()
        val service = DefaultI2cService(Pi4jContextManager)

        val request = i2cRegisterRequest {
            device = i2cDeviceId { bus = 1; address = 0x40 }
            register = 0
        }

        val error = runCatching { runBlocking { service.readRegister(request) } }.exceptionOrNull()
            ?: fail("expected readRegister() to throw instead of returning a response")

        assertTrue(error is StatusException, "expected a StatusException, got $error")
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

    @Test
    fun `SpiService Read fails with FAILED_PRECONDITION instead of success=true`() {
        initRaspberryPiOnlyContext()
        val service = DefaultSpiService(Pi4jContextManager)

        val request = spiReadRequest {
            device = spiDeviceId { bus = 0; chipSelect = 0 }
            length = 4
        }

        val error = runCatching { runBlocking { service.read(request) } }.exceptionOrNull()
            ?: fail("expected read() to throw instead of returning a response")

        assertTrue(error is StatusException, "expected a StatusException, got $error")
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

    @Test
    fun `GpioService SetOutput fails with FAILED_PRECONDITION instead of success=true`() {
        initRaspberryPiOnlyContext()
        val service = GpioServiceImpl(Pi4jContextManager)

        val request = setOutputRequest { pin = 4 }

        val error = runCatching { runBlocking { service.setOutput(request) } }.exceptionOrNull()
            ?: fail("expected setOutput() to throw instead of returning a response")

        assertTrue(error is StatusException, "expected a StatusException, got $error")
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }
}
