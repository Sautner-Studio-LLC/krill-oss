package krill.zone.server

/**
 * Static container for the PinProvider instance.
 * Initialized during server lifecycle startup so the Ktor auth plugin
 * (which installs before Koin is ready) can access it.
 */
object PinProviderContainer {
    var pinProvider: PinProvider? = null

    fun init(provider: PinProvider) {
        this.pinProvider = provider
    }
}
