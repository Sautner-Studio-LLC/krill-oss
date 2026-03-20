package krill.zone

import com.pi4j.Pi4J
import com.pi4j.context.Context
import org.slf4j.LoggerFactory

/**
 * Singleton that owns the single Pi4J [Context] for the lifetime of the service.
 *
 * Pi4J v4 uses the Foreign Function & Memory API (finalized in JDK 22) which is why
 * this daemon must run on JDK 25 while clients can use any JDK version.
 */
object Pi4jContextManager {

    private val log = LoggerFactory.getLogger(Pi4jContextManager::class.java)

    @Volatile
    private var _context: Context? = null

    val context: Context
        get() = _context ?: error("Pi4J context not initialized — call initialize() first")

    val isInitialized: Boolean
        get() = _context != null

    /**
     * Initialize the Pi4J context.
     *
     * On a real Raspberry Pi with the PiGpio plugin on the classpath this will
     * auto-detect all available platforms and providers.
     *
     * Pass [mock] = true (or set env PI4J_MOCK=true) to bring up a stub context
     * that lets the service start without hardware — useful for development/CI.
     */
    fun initialize(mock: Boolean = false) {
        check(_context == null) { "Pi4J context already initialized" }
        log.info("Initializing Pi4J context (mock={})", mock)
        _context = if (mock) buildMockContext() else Pi4J.newAutoContext()
        log.info("Pi4J context ready — platforms: {}", _context!!.platforms().all().keys)
    }

    fun shutdown() {
        _context?.let {
            log.info("Shutting down Pi4J context")
            it.shutdown()
            _context = null
        }
    }

    // ── Mock context ──────────────────────────────────────────────────────────
    // Loaded reflectively so that the pi4j-plugin-mock jar is not a hard
    // compile-time requirement (it may not exist for every pi4j release).

    private fun buildMockContext(): Context {
        return try {
            // Reflective load so pi4j-plugin-mock is not a hard compile dependency
            val platformClass  = Class.forName("com.pi4j.plugin.mock.platform.MockPlatform")
            val platform = platformClass.getDeclaredConstructor().newInstance()
                as com.pi4j.platform.Platform

            Pi4J.newContextBuilder()
                .add(platform)
                .build()
                .also { log.info("Mock platform loaded") }
        } catch (e: ClassNotFoundException) {
            log.warn("pi4j-plugin-mock not on classpath — falling back to auto context")
            Pi4J.newAutoContext()
        }
    }
}
