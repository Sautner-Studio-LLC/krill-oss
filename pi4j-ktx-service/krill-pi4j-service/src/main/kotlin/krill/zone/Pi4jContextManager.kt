package krill.zone

import com.pi4j.*
import com.pi4j.context.*
import com.pi4j.io.IOType
import io.grpc.Status
import org.slf4j.*

/**
 * Singleton that owns the single Pi4J [Context] for the lifetime of the service.
 *
 * Pi4J v4 uses the Foreign Function & Memory API (finalized in JDK 22) which is why
 * this daemon must run on JDK 25 while clients can use any JDK version.
 */
object Pi4jContextManager {

    private val log = LoggerFactory.getLogger(Pi4jContextManager::class.java)

    /**
     * Provider family to select for hardware I/O. `raspberrypi-plugin`'s own providers
     * (the context's default fallback platform) are registry-only stubs that report
     * success without touching hardware — see krill-oss#244. Every IO call site must
     * name one of these families explicitly rather than relying on Pi4J's platform default.
     */
    enum class ProviderFamily { FFM, MOCK }

    private val FFM_PROVIDER_IDS = mapOf(
        IOType.DIGITAL_INPUT to "ffm-digital-input",
        IOType.DIGITAL_OUTPUT to "ffm-digital-output",
        IOType.PWM to "ffm-pwm",
        IOType.I2C to "ffm-i2c",
        IOType.SPI to "ffm-spi",
    )

    private val MOCK_PROVIDER_IDS = mapOf(
        IOType.DIGITAL_INPUT to "mock-digital-input",
        IOType.DIGITAL_OUTPUT to "mock-digital-output",
        IOType.PWM to "mock-pwm",
        IOType.I2C to "mock-i2c",
    )

    /** IOTypes with a real gRPC service in this daemon — the ones worth guarding at startup. */
    private val GUARDED_TYPES = listOf(IOType.DIGITAL_INPUT, IOType.DIGITAL_OUTPUT, IOType.PWM, IOType.I2C)

    @Volatile
    private var _context: Context? = null

    @Volatile
    private var _providerFamily: ProviderFamily = ProviderFamily.FFM

    @Volatile
    private var _degraded: Set<IOType> = emptySet()

    val context: Context
        get() = _context ?: error("Pi4J context not initialized — call initialize() first")

    /**
     * Initialize the Pi4J context.
     *
     * On a real Raspberry Pi with [libs.pi4j.plugin.ffm][com.pi4j.plugin.ffm.FFMPlugin] on
     * the classpath this registers the FFM providers that actually drive hardware — the
     * `raspberrypi-plugin` platform's own providers are stubs and never selected explicitly.
     *
     * Pass [mock] = true (or set env PI4J_MOCK=true) to bring up the Pi4J mock platform
     * instead — lets the service start without hardware, useful for development/CI. The
     * provider family can also be overridden with env PI4J_PROVIDER=FFM|MOCK.
     */
    fun initialize(mock: Boolean = false) {
        check(_context == null) { "Pi4J context already initialized" }
        val family = resolveFamily(mock)
        log.info("Initializing Pi4J context (mock={}, providerFamily={})", mock, family)
        _providerFamily = family
        _context = if (family == ProviderFamily.MOCK) buildMockContext() else Pi4J.newAutoContext()
        log.info("Pi4J context ready — platforms: {}", _context!!.platforms().all().keys)
        _degraded = computeDegradedTypes(_context!!)
    }

    fun shutdown() {
        _context?.let {
            log.info("Shutting down Pi4J context")
            it.shutdown()
            _context = null
        }
    }

    /**
     * Test seam: adopt an already-built [Context] instead of auto-discovering one,
     * so tests can exercise a specific platform/provider combination (e.g. a
     * raspberrypi-only context with no FFM providers) without touching real hardware.
     */
    internal fun initializeForTest(context: Context, family: ProviderFamily) {
        check(_context == null) { "Pi4J context already initialized" }
        _providerFamily = family
        _context = context
        log.info("Pi4J context ready (test) — platforms: {}", context.platforms().all().keys)
        _degraded = computeDegradedTypes(context)
    }

    /** The provider id to select for [type] under the active [ProviderFamily]. */
    fun providerId(type: IOType): String {
        val ids = if (_providerFamily == ProviderFamily.MOCK) MOCK_PROVIDER_IDS else FFM_PROVIDER_IDS
        return ids[type] ?: error("No provider mapping for IOType $type under family $_providerFamily")
    }

    /** True if [type]'s intended provider failed to resolve at startup — see [initialize]. */
    fun isDegraded(type: IOType): Boolean = type in _degraded

    /**
     * Returns the provider id to select for [type], or throws a `FAILED_PRECONDITION`
     * gRPC status if that provider never resolved. Call this before touching hardware —
     * it converts what used to be a silent `success=true` stub response into a loud error.
     */
    fun requireProvider(type: IOType): String {
        if (isDegraded(type)) {
            throw Status.FAILED_PRECONDITION
                .withDescription(
                    "IOType $type has no working Pi4J provider on this host — " +
                        "'${providerId(type)}' never registered, so this call would otherwise " +
                        "silently no-op against a registry-only stub. See krill-oss#244."
                )
                .asException()
        }
        return providerId(type)
    }

    private fun resolveFamily(mock: Boolean): ProviderFamily {
        if (mock || System.getenv("PI4J_MOCK")?.toBoolean() == true) return ProviderFamily.MOCK
        val env = System.getenv("PI4J_PROVIDER")?.uppercase()
        return env?.let { runCatching { ProviderFamily.valueOf(it) }.getOrNull() } ?: ProviderFamily.FFM
    }

    private fun computeDegradedTypes(context: Context): Set<IOType> =
        GUARDED_TYPES.filterTo(mutableSetOf()) { type ->
            val id = providerId(type)
            val healthy = context.hasProvider(id)
            if (!healthy) {
                log.error(
                    "IOType {} has no working provider — expected '{}' never registered. " +
                        "Calls touching this IOType will fail with FAILED_PRECONDITION instead of " +
                        "silently no-op'ing against a stub.",
                    type, id
                )
            }
            !healthy
        }

    // ── Mock context ──────────────────────────────────────────────────────────
    // Loaded reflectively because pi4j-plugin-mock is a testImplementation-only
    // dependency — present on the test runtime classpath, absent from the shipped
    // shadowJar. Unlike the old implementation, a missing jar fails loudly instead
    // of silently falling back to a real hardware context (krill-oss#244).

    private fun buildMockContext(): Context {
        val platformClass = try {
            Class.forName("com.pi4j.plugin.mock.platform.MockPlatform")
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "Mock mode requested (PI4J_MOCK/--mock) but pi4j-plugin-mock is not on the " +
                    "classpath — it is a testImplementation-only dependency in this module, " +
                    "available when running the test suite. The packaged daemon does not ship it.",
                e
            )
        }
        val platform = platformClass.getDeclaredConstructor().newInstance()
                as com.pi4j.platform.Platform

        return Pi4J.newContextBuilder()
            .add(platform)
            .build()
            .also { log.info("Mock platform loaded") }
    }
}
