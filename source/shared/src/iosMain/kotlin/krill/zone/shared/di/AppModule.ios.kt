package krill.zone.shared.di

import co.touchlab.kermit.*
import kotlinx.coroutines.*
import krill.zone.shared.io.*
import org.koin.core.qualifier.*
import org.koin.dsl.*

private val scopeLogger = Logger.withTag("IO_SCOPE")

actual val platformModule = module {
    single<CoroutineScope>(named(IO_SCOPE)) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            scopeLogger.e(throwable) { "Uncaught exception in IO_SCOPE" }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }
    single<FileOperations> {  IosFileOperations() }

}