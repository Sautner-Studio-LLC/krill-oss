package krill.zone.shared

import android.content.*
import android.os.*
import co.touchlab.kermit.*
import org.koin.core.component.*
import java.util.*

private const val INSTALL_ID = "install_id"

// Cache the install ID to ensure consistency across the app lifecycle
// This prevents returning different UUIDs if context access fails after initial success
@Volatile
private var cachedInstallId: String? = null

actual val installId: () -> String
    get() = {
        // Return cached value if available
        cachedInstallId ?: run {
            try {
                val store: SharedPreferences = ContextContainer.context.getSharedPreferences("krill", Context.MODE_PRIVATE)
                val id = if (!store.contains(INSTALL_ID)) {
                    val newId = UUID.randomUUID().toString()
                    // Use commit() instead of apply() to ensure synchronous write
                    store.edit().putString(INSTALL_ID, newId).commit()
                    newId
                } else {
                    store.getString(INSTALL_ID, null) ?: UUID.randomUUID().toString().also { newId ->
                        store.edit().putString(INSTALL_ID, newId).commit()
                    }
                }
                cachedInstallId = id
                id
            } catch (ex: Exception) {
                Logger.withTag("Platform.android").e(ex) { "Error accessing install ID, using fallback" }
                // If we already have a cached ID, use it; otherwise generate one and cache it
                cachedInstallId ?: UUID.randomUUID().toString().also { cachedInstallId = it }
            }
        }
    }
actual val hostName: String
    get() = Build.MODEL

actual val platform: Platform
    get() = Platform.ANDROID

object ContextContainer: KoinComponent {
    val context: Context  by inject()
}

