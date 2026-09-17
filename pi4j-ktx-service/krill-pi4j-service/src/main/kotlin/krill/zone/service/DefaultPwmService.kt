package krill.zone.service

import com.krillforge.pi4j.proto.*
import com.pi4j.io.IOType
import com.pi4j.ktx.io.*
import io.grpc.*
import krill.zone.*
import org.slf4j.*
import java.io.File
import java.util.concurrent.*

/**
 * gRPC service implementation for PWM channels.
 *
 * Each [PwmConfig.pin] maps to one Pi4J [com.pi4j.io.pwm.Pwm] instance.
 * Duty cycle and frequency can be updated independently without reconfiguring
 * the channel from scratch.
 *
 * [SetPulse] is a separate, parallel path: it bypasses Pi4J entirely and writes
 * `/sys/class/pwm` directly for nanosecond-exact pulse widths (Pi4J's PWM interface
 * is `Integer`-percent end to end, which cannot express servo-grade resolution — see
 * krill-oss#246). [pwmSysfsRoot] is injectable so tests can point it at a fake sysfs
 * tree instead of the real hardware path.
 */
class DefaultPwmService(
    private val ctx: Pi4jContextManager = Pi4jContextManager,
    private val pwmSysfsRoot: File = File("/sys/class/pwm"),
    private val sysfsWrite: (File, String) -> Unit = { file, value -> file.writeText(value) },
) : PwmServiceGrpcKt.PwmServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(DefaultPwmService::class.java)

    private val channels = ConcurrentHashMap<Int, com.pi4j.io.pwm.Pwm>()

    /** Channels driven via [setPulse] — tracked separately since they bypass [channels]/Pi4J. */
    private val pulseChannels = ConcurrentHashMap<Int, File>()

    // ── Configure ─────────────────────────────────────────────────────────────

    override suspend fun configure(request: PwmConfig): PwmResponse = runCatchingGrpc {
        val providerId = ctx.requireProvider(IOType.PWM)

        // Remove any existing channel so the new config takes effect
        channels.remove(request.pin)?.off()

        log.debug("Configuring PWM pin {} @ {}Hz {}%", request.pin, request.frequency, request.dutyCycle)
        val ch = ctx.context.pwm(request.pin) {
            provider(providerId)
            if (request.id.isNotBlank()) id(request.id)
            frequency(request.frequency)
            dutyCycle(request.dutyCycle.toInt())
            initial(request.dutyCycle.toInt())
            shutdown(0)
        }
        ch.on(request.dutyCycle.toInt(), request.frequency)
        channels[request.pin] = ch

        pwmResponse {
            success = true
            actualFrequency = ch.frequency()
            actualDutyCycle = ch.dutyCycle().toFloat()
        }
    }.getOrElse { e ->
        log.warn("configure PWM pin {}: {}", request.pin, e.message)
        pwmResponse { success = false; message = e.message.orEmpty() }
    }

    // ── SetDutyCycle ──────────────────────────────────────────────────────────

    override suspend fun setDutyCycle(request: DutyCycleRequest): PwmResponse {
        val ch = channels[request.pin] ?: throw Status.NOT_FOUND
            .withDescription("PWM pin ${request.pin} not configured — call Configure first")
            .asException()
        return runCatching {
            ch.on(request.dutyCycle.toInt())
            pwmResponse {
                success = true
                actualFrequency = ch.frequency()
                actualDutyCycle = ch.dutyCycle().toFloat()
            }
        }.getOrElse { e ->
            pwmResponse { success = false; message = e.message.orEmpty() }
        }
    }

    // ── SetFrequency ──────────────────────────────────────────────────────────

    override suspend fun setFrequency(request: FrequencyRequest): PwmResponse {
        val ch = channels[request.pin] ?: throw Status.NOT_FOUND
            .withDescription("PWM pin ${request.pin} not configured — call Configure first")
            .asException()
        return runCatching {
            // Re-apply with updated frequency, keeping current duty cycle
            ch.on(ch.dutyCycle(), request.frequency)
            pwmResponse {
                success = true
                actualFrequency = ch.frequency()
                actualDutyCycle = ch.dutyCycle().toFloat()
            }
        }.getOrElse { e ->
            pwmResponse { success = false; message = e.message.orEmpty() }
        }
    }

    // ── SetPulse ──────────────────────────────────────────────────────────────

    override suspend fun setPulse(request: PulseWidthRequest): PwmResponse = runCatchingGrpc {
        require(request.periodNs > 0) { "period_ns must be positive, got ${request.periodNs}" }
        require(request.dutyNs in 0..request.periodNs) {
            "duty_ns must be between 0 and period_ns — got duty_ns=${request.dutyNs}, period_ns=${request.periodNs}"
        }

        val channelDir = resolveChannelDir(request.pin)
        log.debug("Setting PWM pulse: pin={} period_ns={} duty_ns={}", request.pin, request.periodNs, request.dutyNs)

        // Disable and reset duty_cycle to 0 before changing period — some drivers reject a
        // period write while the channel is enabled, and all of them reject a duty_cycle
        // write that exceeds the *currently applied* period. Both writes are no-ops on a
        // freshly-exported channel (enable/duty_cycle already 0), so this is safe whether
        // this is the first SetPulse on this channel or a reconfigure of a running one.
        writeSysfsAttr(channelDir, "enable", "0")
        writeSysfsAttr(channelDir, "duty_cycle", "0")
        writeSysfsAttr(channelDir, "period", request.periodNs.toString())
        writeSysfsAttr(channelDir, "duty_cycle", request.dutyNs.toString())
        writeSysfsAttr(channelDir, "enable", "1")

        pulseChannels[request.pin] = channelDir

        pwmResponse {
            success = true
            actualFrequency = frequencyHzOf(request.periodNs)
            actualDutyCycle = dutyCyclePercentOf(request.dutyNs, request.periodNs)
        }
    }.getOrElse { e ->
        pwmResponse { success = false; message = e.message.orEmpty() }
    }

    private fun frequencyHzOf(periodNs: Long): Int = Math.round(1_000_000_000.0 / periodNs).toInt()

    private fun dutyCyclePercentOf(dutyNs: Long, periodNs: Long): Float =
        (dutyNs.toDouble() / periodNs * 100).toFloat()

    /**
     * Resolves the sysfs directory for PWM channel [channel] under the single
     * `pwmchipN` on this host, exporting it if it is not already exported.
     *
     * Throws [Status.FAILED_PRECONDITION] with the Pi 5 overlay remediation if
     * [pwmSysfsRoot] has no `pwmchipN` at all — that means `dtoverlay=pwm`/`pwm-2chan`
     * was never added to `/boot/firmware/config.txt` (or the host wasn't rebooted
     * after adding it), so there is no hardware PWM to write to.
     */
    private fun resolveChannelDir(channel: Int): File {
        val chip = pwmSysfsRoot.takeIf { it.isDirectory }
            ?.listFiles { f -> f.isDirectory && f.name.startsWith("pwmchip") }
            ?.sortedBy { it.name }
            ?.firstOrNull()
            ?: throw missingOverlayError()

        val channelDir = File(chip, "pwm$channel")
        if (channelDir.isDirectory) return channelDir

        runCatching { sysfsWrite(File(chip, "export"), channel.toString()) }
            .onFailure { e ->
                // A losing writer in a concurrent-export race gets EBUSY from the kernel even
                // though the channel the winner just exported is now genuinely usable — only
                // treat this as a real failure if the channel still isn't there.
                if (!channelDir.isDirectory) {
                    throw Status.FAILED_PRECONDITION
                        .withDescription("Failed to export PWM channel $channel on ${chip.path} — ${e.message}")
                        .asException()
                }
            }

        if (!channelDir.isDirectory) {
            throw Status.FAILED_PRECONDITION
                .withDescription(
                    "PWM channel $channel did not appear under ${chip.path} after export — " +
                        "the chip may not have that many channels, or another process holds it."
                )
                .asException()
        }
        return channelDir
    }

    private fun writeSysfsAttr(channelDir: File, attr: String, value: String) {
        sysfsWrite(File(channelDir, attr), value)
    }

    private fun missingOverlayError(): StatusException = Status.FAILED_PRECONDITION
        .withDescription(
            "No PWM chip found under ${pwmSysfsRoot.path} — hardware PWM is not enabled on this host. " +
                "Add 'dtoverlay=pwm-2chan' (or 'dtoverlay=pwm' for a single channel) to " +
                "/boot/firmware/config.txt and reboot — the overlay is required even though the pins " +
                "are already broken out, and SetPulse's pin argument is the PWM CHANNEL (0-3), not the " +
                "GPIO number. Note: pwm-2chan conflicts with dtparam=audio=on — both use the same PWM " +
                "channels, and enabling both silently breaks one of them with no error " +
                "· https://github.com/Sautner-Studio-LLC/krill-oss/blob/main/pi4j-ktx-service/README.md#hardware-pwm-setup-pi-5"
        )
        .asException()

    // ── Stop ──────────────────────────────────────────────────────────────────

    override suspend fun stop(request: PinAddress): PwmResponse {
        val ch = channels.remove(request.pin)
        val pulseDir = pulseChannels.remove(request.pin)
        if (ch == null && pulseDir == null) {
            throw Status.NOT_FOUND
                .withDescription("PWM pin ${request.pin} not configured")
                .asException()
        }
        return runCatching {
            ch?.off()
            pulseDir?.let { writeSysfsAttr(it, "enable", "0") }
            log.debug("PWM pin {} stopped", request.pin)
            pwmResponse { success = true }
        }.getOrElse { e ->
            pwmResponse { success = false; message = e.message.orEmpty() }
        }
    }

    // ── GetStatus ─────────────────────────────────────────────────────────────

    override suspend fun getStatus(request: PinAddress): PwmStatus {
        val ch = channels[request.pin]
        if (ch != null) {
            return pwmStatus {
                pin = request.pin
                running = ch.isOn
                frequency = ch.frequency()
                dutyCycle = ch.dutyCycle().toFloat()
            }
        }
        val pulseDir = pulseChannels[request.pin]
        if (pulseDir != null) {
            return runCatching {
                val periodNs = File(pulseDir, "period").readText().trim().toLong()
                val dutyNs = File(pulseDir, "duty_cycle").readText().trim().toLong()
                val enabled = File(pulseDir, "enable").readText().trim() == "1"
                pwmStatus {
                    pin = request.pin
                    running = enabled
                    frequency = if (periodNs > 0) frequencyHzOf(periodNs) else 0
                    dutyCycle = if (periodNs > 0) dutyCyclePercentOf(dutyNs, periodNs) else 0f
                }
            }.getOrElse { e ->
                log.warn("getStatus PWM pin {}: failed to read sysfs state: {}", request.pin, e.message)
                pwmStatus { pin = request.pin }
            }
        }
        return pwmStatus { pin = request.pin }
    }
}
