package krill.zone.app

import co.touchlab.kermit.*
import org.slf4j.*

class JvmLogWriter : LogWriter() {
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        val logger = LoggerFactory.getLogger("DesktopApp")
        when (severity) {
            Severity.Verbose -> logger.debug("[$tag] $message")
            Severity.Debug -> logger.debug("[$tag] $message")
            Severity.Info ->logger.info("[$tag] $message")
            Severity.Warn -> logger.warn("[$tag] $message")
            Severity.Error -> logger.error("[$tag] $message", throwable)
            Severity.Assert -> logger.debug("[$tag] $message")
        }

    }

}