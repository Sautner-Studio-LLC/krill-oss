package krill.zone.shared.feature

/**
 * Android implementation — reads from the classpath.
 * Resources in `shared/src/commonMain/resources/` are bundled into the AAR
 * and available via the thread's context ClassLoader.
 */
actual suspend fun readClasspathResource(name: String): String? {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(name)
        ?: FeatureLoaderAndroid::class.java.classLoader?.getResourceAsStream(name)
    return stream?.bufferedReader()?.use { it.readText() }
}

/** Anchor class so we can reference its ClassLoader as a fallback. */
private class FeatureLoaderAndroid

