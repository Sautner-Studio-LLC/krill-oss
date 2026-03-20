package krill.zone.shared.feature

/**
 * JVM implementation — reads from the classpath.
 * Resources in `shared/src/commonMain/resources/` are bundled into the JAR
 * and available to all JVM consumers (server, desktop).
 */
actual suspend fun readClasspathResource(name: String): String? {
    val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(name)
        ?: FeatureLoader::class.java.classLoader?.getResourceAsStream(name)
    return stream?.bufferedReader()?.use { it.readText() }
}

/** Anchor class so we can reference its ClassLoader as a fallback. */
private class FeatureLoader

