package krill.zone.service

import com.krillforge.pi4j.proto.*
import com.pi4j.io.IOType
import com.pi4j.ktx.io.*
import io.grpc.*
import krill.zone.*
import org.slf4j.*
import java.util.concurrent.*
import kotlin.math.roundToInt

/**
 * gRPC service implementation for PWM channels.
 *
 * Each [PwmConfig.pin] maps to one Pi4J [com.pi4j.io.pwm.Pwm] instance.
 * Duty cycle and frequency can be updated independently without reconfiguring
 * the channel from scratch.
 */
class DefaultPwmService(
    private val ctx: Pi4jContextManager = Pi4jContextManager
) : PwmServiceGrpcKt.PwmServiceCoroutineImplBase() {

    private val log = LoggerFactory.getLogger(DefaultPwmService::class.java)

    private val channels = ConcurrentHashMap<Int, com.pi4j.io.pwm.Pwm>()

    // ── Configure ─────────────────────────────────────────────────────────────

    override suspend fun configure(request: PwmConfig): PwmResponse = runCatchingGrpc {
        val providerId = ctx.requireProvider(IOType.PWM)
        // Pi4J's PWM API only accepts an Integer percent (see krill-oss#245) — round
        // rather than truncate so error is symmetric (±0.5%) instead of always downward.
        val roundedDutyCycle = request.dutyCycle.roundToInt()

        // Remove any existing channel so the new config takes effect
        channels.remove(request.pin)?.off()

        log.debug("Configuring PWM pin {} @ {}Hz {}%", request.pin, request.frequency, request.dutyCycle)
        val ch = ctx.context.pwm(request.pin) {
            provider(providerId)
            if (request.id.isNotBlank()) id(request.id)
            frequency(request.frequency)
            dutyCycle(roundedDutyCycle)
            initial(roundedDutyCycle)
            shutdown(0)
        }
        ch.on(roundedDutyCycle, request.frequency)
        channels[request.pin] = ch

        pwmResponse {
            success = true
            actualFrequency = ch.frequency()
            actualDutyCycle = ch.dutyCycle().toFloat()
            requestedDutyCycle = request.dutyCycle
            quantized = roundedDutyCycle.toFloat() != request.dutyCycle
            provider = providerId
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
            val roundedDutyCycle = request.dutyCycle.roundToInt()
            ch.on(roundedDutyCycle)
            pwmResponse {
                success = true
                actualFrequency = ch.frequency()
                actualDutyCycle = ch.dutyCycle().toFloat()
                requestedDutyCycle = request.dutyCycle
                quantized = roundedDutyCycle.toFloat() != request.dutyCycle
                provider = ctx.providerId(IOType.PWM)
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

    // ── Stop ──────────────────────────────────────────────────────────────────

    override suspend fun stop(request: PinAddress): PwmResponse {
        val ch = channels.remove(request.pin) ?: throw Status.NOT_FOUND
            .withDescription("PWM pin ${request.pin} not configured")
            .asException()
        return runCatching {
            ch.off()
            log.debug("PWM pin {} stopped", request.pin)
            pwmResponse { success = true }
        }.getOrElse { e ->
            pwmResponse { success = false; message = e.message.orEmpty() }
        }
    }

    // ── GetStatus ─────────────────────────────────────────────────────────────

    override suspend fun getStatus(request: PinAddress): PwmStatus {
        val ch = channels[request.pin]
        return pwmStatus {
            pin = request.pin
            running = ch != null && ch.isOn
            frequency = ch?.frequency() ?: 0
            dutyCycle = ch?.dutyCycle()?.toFloat() ?: 0f
        }
    }
}
