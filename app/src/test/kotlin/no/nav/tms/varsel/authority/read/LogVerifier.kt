package no.nav.tms.varsel.authority.read

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList


class MapAppender : AppenderBase<ILoggingEvent>() {
    private val events: MutableList<ILoggingEvent> = CopyOnWriteArrayList()

    override fun append(event: ILoggingEvent) {
        events.add(event)
    }

    fun getEvents(): List<ILoggingEvent> = events

    fun clear() = events.clear()
}

object LogVerifier {
    private val logger = LoggerFactory.getLogger("auditLog") as Logger

    val appender: MapAppender
        get() = logger.getAppender("auditLogger") as MapAppender

    fun verifyLogCount(expectedSize: Int) {
        val events = appender.getEvents()
        withClue("Log contains the wrong number of events") {
            appender.getEvents().size shouldBe expectedSize
        }
    }

    fun verifyAction(
        action: String,
        expectedAdmin: String? = null,
        expectedIdent: String? = null,
        expectedMessage: String? = null,
        expectedJirasak: String? = null,
    ) {
        val events = appender.getEvents()
        val actionEvent = events.firstOrNull { it.message.contains(action) }
        withClue("Fant ingen auditlog entry for $action") {
            actionEvent shouldNotBe null
        }

        actionEvent?.message.let { auditlogMessage ->
            withClue("Feil i auditlog format") {
                if (expectedAdmin != null) {
                    auditlogMessage shouldContain "suid=$expectedAdmin"
                }
                if (expectedIdent != null) {
                    auditlogMessage shouldContain "duid=$expectedIdent"
                }
                if (expectedMessage != null) {
                    auditlogMessage shouldContain "msg=$expectedMessage"
                }
                if (expectedJirasak != null) {
                    auditlogMessage shouldContain "flexString1Label=Jirasak"
                    auditlogMessage shouldContain "flexString1=$expectedJirasak"
                }

            }
        }
    }

    fun clear() = appender.clear()
}


