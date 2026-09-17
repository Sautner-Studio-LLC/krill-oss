package krill.zone

import com.pi4j.Pi4J
import com.pi4j.io.IOType
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalInputProviderImpl
import com.pi4j.plugin.mock.provider.gpio.digital.MockDigitalOutputProviderImpl
import com.pi4j.plugin.mock.provider.i2c.MockI2CProviderImpl
import com.pi4j.plugin.mock.provider.pwm.MockPwmProviderImpl
import com.pi4j.plugin.mock.provider.spi.MockSpiProviderImpl
import com.pi4j.provider.Provider
import krill.zone.Pi4jContextManager.ProviderFamily
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Regression coverage for krill-oss#244: Pi4J's `raspberrypi` platform default is a
 * registry-only stub. These assert the mock providers resolve as themselves — never as
 * the raspberrypi stubs — both directly on a Pi4J [com.pi4j.context.Context] and through
 * [Pi4jContextManager]'s provider-family bookkeeping.
 *
 * The context is built by registering the four mock provider instances directly
 * ([com.pi4j.context.ContextBuilder.add]) rather than via plugin/platform auto-detection —
 * auto-detection depends on board-type detection and OS group membership (see
 * [StubProviderGuardTest]'s findings on `FFMPermissionHelper`), which would make this test's
 * outcome depend on the host it runs on. Registering providers directly is deterministic
 * on any machine, including CI.
 */
class ProviderResolutionTest {

    private val stubProviderIds = setOf(
        "raspberrypi-digital-input", "raspberrypi-digital-output",
        "raspberrypi-pwm", "raspberrypi-i2c", "raspberrypi-spi",
    )

    @AfterTest
    fun tearDown() {
        Pi4jContextManager.shutdown()
    }

    private fun buildMockOnlyContext() = Pi4J.newContextBuilder()
        .add(
            MockDigitalInputProviderImpl(),
            MockDigitalOutputProviderImpl(),
            MockPwmProviderImpl(),
            MockI2CProviderImpl(),
            MockSpiProviderImpl(),
        )
        .build()

    @Test
    fun `mock providers resolve as themselves, never as the raspberrypi stubs`() {
        val context = buildMockOnlyContext()
        try {
            assertEquals("mock-digital-input", context.provider<Provider<*, *, *>>(IOType.DIGITAL_INPUT).id())
            assertEquals("mock-digital-output", context.provider<Provider<*, *, *>>(IOType.DIGITAL_OUTPUT).id())
            assertEquals("mock-pwm", context.provider<Provider<*, *, *>>(IOType.PWM).id())
            assertEquals("mock-i2c", context.provider<Provider<*, *, *>>(IOType.I2C).id())
            assertEquals("mock-spi", context.provider<Provider<*, *, *>>(IOType.SPI).id())

            for (type in listOf(IOType.DIGITAL_INPUT, IOType.DIGITAL_OUTPUT, IOType.PWM, IOType.I2C, IOType.SPI)) {
                assertFalse(
                    context.provider<Provider<*, *, *>>(type).id() in stubProviderIds,
                    "resolved provider for $type must not be a raspberrypi stub"
                )
            }
        } finally {
            context.shutdown()
        }
    }

    @Test
    fun `Pi4jContextManager marks every guarded IOType healthy under the mock family`() {
        val context = buildMockOnlyContext()
        Pi4jContextManager.initializeForTest(context, ProviderFamily.MOCK)

        for (type in listOf(IOType.DIGITAL_INPUT, IOType.DIGITAL_OUTPUT, IOType.PWM, IOType.I2C, IOType.SPI)) {
            assertFalse(Pi4jContextManager.isDegraded(type), "$type should resolve under the mock family")
        }
        assertEquals("mock-pwm", Pi4jContextManager.providerId(IOType.PWM))
        assertEquals("mock-spi", Pi4jContextManager.providerId(IOType.SPI))
    }
}
