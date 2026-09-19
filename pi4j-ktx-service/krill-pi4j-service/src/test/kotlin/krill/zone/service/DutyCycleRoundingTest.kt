package krill.zone.service

import com.krillforge.pi4j.proto.dutyCycleRequest
import com.krillforge.pi4j.proto.pwmConfig
import com.pi4j.Pi4J
import com.pi4j.plugin.mock.provider.pwm.MockPwmProviderImpl
import kotlinx.coroutines.runBlocking
import krill.zone.Pi4jContextManager
import krill.zone.Pi4jContextManager.ProviderFamily
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for krill-oss#245: `DefaultPwmService` used to truncate the
 * requested duty cycle to `Int` (always rounding down) at every call site that reaches
 * Pi4J's Integer-percent PWM API. It must instead round to the nearest percent and
 * report the quantization on the wire rather than discarding it silently.
 *
 * Each case gets its own Pi4J context — reconfiguring the same PWM id twice in one
 * context hits an unrelated Pi4J registry restriction ("IO instance already exists"),
 * which isn't what this test is about.
 */
class DutyCycleRoundingTest {

    @AfterTest
    fun tearDown() {
        Pi4jContextManager.shutdown()
    }

    private fun freshMockService(): DefaultPwmService {
        Pi4jContextManager.shutdown()
        val context = Pi4J.newContextBuilder().add(MockPwmProviderImpl()).build()
        Pi4jContextManager.initializeForTest(context, ProviderFamily.MOCK)
        return DefaultPwmService(Pi4jContextManager)
    }

    private val cases = listOf(
        7.4f to 7,
        7.5f to 8,
        7.6f to 8,
        0.4f to 0,
        99.6f to 100,
    )

    @Test
    fun `configure rounds half up instead of truncating`() {
        for ((requested, expected) in cases) {
            val service = freshMockService()
            val response = runBlocking {
                service.configure(pwmConfig { pin = 18; frequency = 50; dutyCycle = requested })
            }

            assertTrue(response.success, "configure($requested) should succeed: ${response.message}")
            assertEquals(expected.toFloat(), response.actualDutyCycle, "actualDutyCycle for requested=$requested")
            assertEquals(requested, response.requestedDutyCycle, "requestedDutyCycle for requested=$requested")
            assertEquals("mock-pwm", response.provider)
        }
    }

    @Test
    fun `setDutyCycle rounds half up instead of truncating`() {
        for ((requested, expected) in cases) {
            val service = freshMockService()
            runBlocking { service.configure(pwmConfig { pin = 18; frequency = 50; dutyCycle = 0f }) }

            val response = runBlocking {
                service.setDutyCycle(dutyCycleRequest { pin = 18; dutyCycle = requested })
            }

            assertTrue(response.success, "setDutyCycle($requested) should succeed: ${response.message}")
            assertEquals(expected.toFloat(), response.actualDutyCycle, "actualDutyCycle for requested=$requested")
            assertEquals(requested, response.requestedDutyCycle, "requestedDutyCycle for requested=$requested")
        }
    }

    @Test
    fun `quantized is true only when rounding changed the value`() {
        val exactService = freshMockService()
        val exact = runBlocking {
            exactService.configure(pwmConfig { pin = 18; frequency = 50; dutyCycle = 8f })
        }
        assertFalse(exact.quantized, "an already-whole percent must not be reported as quantized")

        val roundedService = freshMockService()
        val rounded = runBlocking {
            roundedService.configure(pwmConfig { pin = 18; frequency = 50; dutyCycle = 7.5f })
        }
        assertTrue(rounded.quantized, "a value that required rounding must be reported as quantized")
    }
}
