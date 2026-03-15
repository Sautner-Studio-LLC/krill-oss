package krill.zone.server

import co.touchlab.kermit.*
import io.ktor.server.application.*
import krill.zone.logging.*
import krill.zone.server.logging.*

class ServerLogWriter(val application: Application) : LogWriter() {
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {

        when (severity) {
            Severity.Verbose -> application.log.debug("[$tag] $message")
            Severity.Debug -> application.log.debug("[$tag] $message")
            Severity.Info ->application.log.info("[$tag] $message")
            Severity.Warn -> application.log.warn("[$tag] $message")
            Severity.Error -> {
                throwable?.let {
                    ErrorContainer.add(Error(0, message))
                }

                application.log.error("[$tag] $message", throwable)

            }
            Severity.Assert -> application.log.debug("[$tag] $message")
        }

    }

}