package krill.zone.server.db

import co.touchlab.kermit.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.*
import org.koin.ext.*
import java.io.*

/**
 * Database configuration for H2 embedded database
 */
object DatabaseConfig {
    private val logger = Logger.withTag(DatabaseConfig::class.getFullName())
    
    private const val DATABASE_PATH = "/srv/krill/data/krill_v3.db"

    
    /**
     * Initialize the H2 database connection and create tables
     */
    fun init() {
        try {
            // Ensure the data directory exists
            val dbFile = File(DATABASE_PATH)
            dbFile.parentFile?.mkdirs()
            
            // Connect to H2 database with auto-server mode for better lock handling
            // AUTO_SERVER=TRUE: Allows automatic server mode if another process connects
            // FILE_LOCK=SOCKET: Uses socket-based locking which recovers better from crashes
            Database.connect(
                url = "jdbc:h2:file:$DATABASE_PATH;DB_CLOSE_ON_EXIT=TRUE;AUTO_SERVER=TRUE;FILE_LOCK=SOCKET",
                driver = "org.h2.Driver",
                // Explicitly provide connection registration to avoid ServiceLoader issues with ProGuard/R8 obfuscation
                connectionAutoRegistration = ExposedConnectionImpl()
            )
            
            logger.i("Database initialized at: $DATABASE_PATH")
            
            // Create tables if they don't exist
            transaction {
                SchemaUtils.create(NodesTable)
            }
            
            logger.i("Database tables created/verified")
        } catch (e: Exception) {
            logger.e("Failed to initialize database", e)
            throw e
        }
    }
}
