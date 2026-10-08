package krill.zone.service

import com.krillforge.pi4j.proto.pinAddress
import com.krillforge.pi4j.proto.pulseWidthRequest
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Regression coverage for krill-oss#246: `SetPulse` writes `/sys/class/pwm` directly
 * (Pi4J's PWM interface is `Integer`-percent end to end and cannot express servo-grade
 * resolution). [sysfsRoot] is a fake tree under a JUnit temp dir — never the real
 * `/sys/class/pwm` — so these are deterministic on any host, including CI with no PWM
 * hardware at all (see workflow.md "no real production paths in tests").
 */
class DefaultPwmServiceSetPulseTest {

    private lateinit var sysfsRoot: File
    private lateinit var service: DefaultPwmService

    @BeforeTest
    fun setUp() {
        sysfsRoot = Files.createTempDirectory("pwm-sysfs-test").toFile()
        service = DefaultPwmService(pwmSysfsRoot = sysfsRoot)
    }

    @AfterTest
    fun tearDown() {
        sysfsRoot.deleteRecursively()
    }

    private fun preExportedChannel(chip: String = "pwmchip0", channel: Int = 0): File {
        val channelDir = File(sysfsRoot, "$chip/pwm$channel")
        channelDir.mkdirs()
        File(channelDir, "period").writeText("0")
        File(channelDir, "duty_cycle").writeText("0")
        File(channelDir, "enable").writeText("0")
        return channelDir
    }

    @Test
    fun `setPulse writes period, duty_cycle, and enable to sysfs for an already-exported channel`() = runBlocking {
        val channelDir = preExportedChannel()

        val response = service.setPulse(pulseWidthRequest {
            pin = 0
            periodNs = 20_000_000
            dutyNs = 1_500_000
        })

        assertTrue(response.success, "expected success, got: ${response.message}")
        assertEquals("20000000", File(channelDir, "period").readText())
        assertEquals("1500000", File(channelDir, "duty_cycle").readText())
        assertEquals("1", File(channelDir, "enable").readText())
        assertEquals(50, response.actualFrequency)
        assertEquals(7.5f, response.actualDutyCycle)
    }

    @Test
    fun `setPulse rounds actualFrequency instead of truncating`() = runBlocking {
        preExportedChannel()

        // 1e9 / 7_000_000 = 142.857... — must round to 143, not truncate to 142.
        val response = service.setPulse(pulseWidthRequest { pin = 0; periodNs = 7_000_000; dutyNs = 1_000_000 })

        assertTrue(response.success, "expected success, got: ${response.message}")
        assertEquals(143, response.actualFrequency)
    }

    @Test
    fun `setPulse writes disable-reset-reconfigure-enable in an order a real kernel driver would accept`() = runBlocking {
        // Simulates the two sysfs constraints real PWM drivers enforce: a period write is
        // rejected while enable=1, and a duty_cycle write is rejected if it exceeds the
        // currently-applied period. Any write-order bug (e.g. writing period before
        // disabling, or the new duty_cycle before the new period) throws here exactly like
        // it would on real hardware — this could not be verified with plain file writes.
        var enabled = false
        var currentPeriod = 0L
        val kernelSim: (File, String) -> Unit = { file, value ->
            when (file.name) {
                "enable" -> enabled = value == "1"
                "period" -> {
                    check(!enabled) { "driver rejects period write while enabled" }
                    currentPeriod = value.toLong()
                }
                "duty_cycle" -> {
                    val duty = value.toLong()
                    check(duty <= currentPeriod) { "driver rejects duty_cycle ($duty) > period ($currentPeriod)" }
                }
            }
            file.writeText(value)
        }
        val simulatedService = DefaultPwmService(pwmSysfsRoot = sysfsRoot, sysfsWrite = kernelSim)
        val channelDir = preExportedChannel()

        // First call on a fresh (never-enabled) channel.
        val first = simulatedService.setPulse(pulseWidthRequest { pin = 0; periodNs = 20_000_000; dutyNs = 1_500_000 })
        assertTrue(first.success, "expected success, got: ${first.message}")

        // Reconfigure while already running — exercises the enable=0-before-period-write path.
        val second = simulatedService.setPulse(pulseWidthRequest { pin = 0; periodNs = 10_000_000; dutyNs = 500_000 })
        assertTrue(second.success, "expected success, got: ${second.message}")
        assertEquals("10000000", File(channelDir, "period").readText())
        assertEquals("500000", File(channelDir, "duty_cycle").readText())
        assertEquals("1", File(channelDir, "enable").readText())
    }

    @Test
    fun `setPulse succeeds when export loses a concurrent-export race but the channel exists anyway`() = runBlocking {
        // Simulates two callers racing to export the same not-yet-exported channel: the
        // kernel returns EBUSY to whichever write loses, even though the winner's export
        // made the channel genuinely usable a moment later.
        File(sysfsRoot, "pwmchip0").mkdirs()
        val racyWrite: (File, String) -> Unit = { file, value ->
            if (file.name == "export") {
                preExportedChannel() // simulate the concurrent winner materializing the channel
                throw java.io.IOException("Device or resource busy")
            }
            file.writeText(value)
        }
        val racyService = DefaultPwmService(pwmSysfsRoot = sysfsRoot, sysfsWrite = racyWrite)

        val response = racyService.setPulse(pulseWidthRequest { pin = 0; periodNs = 20_000_000; dutyNs = 1_500_000 })

        assertTrue(response.success, "expected the export race to be tolerated, got: ${response.message}")
    }

    @Test
    fun `setPulse fails with FAILED_PRECONDITION when no pwmchip exists under the sysfs root`() {
        // sysfsRoot exists but is empty — simulates a host that never got the dtoverlay line.
        val error = runCatching {
            runBlocking {
                service.setPulse(pulseWidthRequest { pin = 0; periodNs = 20_000_000; dutyNs = 1_500_000 })
            }
        }.exceptionOrNull() ?: fail("expected setPulse() to throw when no pwmchip exists")

        assertTrue(error is StatusException, "expected a StatusException, got $error")
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
        assertTrue(error.status.description.orEmpty().contains("dtoverlay=pwm"), "expected overlay remediation in: ${error.status.description}")
    }

    @Test
    fun `setPulse fails with FAILED_PRECONDITION when the sysfs root itself does not exist`() {
        val missingRoot = File(sysfsRoot, "does-not-exist")
        val serviceWithMissingRoot = DefaultPwmService(pwmSysfsRoot = missingRoot)

        val error = runCatching {
            runBlocking {
                serviceWithMissingRoot.setPulse(pulseWidthRequest { pin = 0; periodNs = 20_000_000; dutyNs = 1_500_000 })
            }
        }.exceptionOrNull() ?: fail("expected setPulse() to throw when the sysfs root is absent")

        assertTrue(error is StatusException)
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
    }

    @Test
    fun `setPulse fails with FAILED_PRECONDITION when export never materializes the channel dir`() {
        // Chip directory exists but the channel was never exported, and this fake filesystem
        // has no kernel behind it to create pwm0/ on a write to export — so this deterministically
        // exercises the "export did not take" failure path without any real hardware.
        File(sysfsRoot, "pwmchip0").mkdirs()

        val error = runCatching {
            runBlocking {
                service.setPulse(pulseWidthRequest { pin = 0; periodNs = 20_000_000; dutyNs = 1_500_000 })
            }
        }.exceptionOrNull() ?: fail("expected setPulse() to throw when export doesn't materialize the channel")

        assertTrue(error is StatusException)
        assertEquals(Status.Code.FAILED_PRECONDITION, error.status.code)
        assertTrue(error.status.description.orEmpty().contains("did not appear"))
    }

    @Test
    fun `setPulse rejects duty_ns greater than period_ns without touching sysfs`() = runBlocking {
        val channelDir = preExportedChannel()

        val response = service.setPulse(pulseWidthRequest {
            pin = 0
            periodNs = 20_000_000
            dutyNs = 25_000_000
        })

        assertFalse(response.success)
        assertTrue(response.message.contains("duty_ns"), "expected validation message, got: ${response.message}")
        assertEquals("0", File(channelDir, "period").readText(), "period must be untouched on validation failure")
    }

    @Test
    fun `stop disables a pulse channel and getStatus reflects it`() = runBlocking {
        preExportedChannel()
        service.setPulse(pulseWidthRequest { pin = 0; periodNs = 20_000_000; dutyNs = 1_500_000 })

        val status = service.getStatus(pinAddress { pin = 0 })
        assertTrue(status.running)
        assertEquals(50, status.frequency)

        val stopped = service.stop(pinAddress { pin = 0 })
        assertTrue(stopped.success)

        val afterStop = service.getStatus(pinAddress { pin = 0 })
        assertFalse(afterStop.running)
    }
}
