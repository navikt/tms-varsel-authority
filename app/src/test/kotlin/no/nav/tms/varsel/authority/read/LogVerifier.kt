package no.nav.tms.varsel.authority.read

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import ch.qos.logback.core.Layout
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList


class TestAppender : AppenderBase<ILoggingEvent>() {
    var layout: Layout<ILoggingEvent>? = null
    private val events: MutableList<String> = CopyOnWriteArrayList()

    override fun start() {
        val configuredLayout = checkNotNull(layout) { "TestAppender requires a layout" }
        check(configuredLayout.isStarted) { "TestAppender requires a started layout" }
        super.start()
    }

    override fun append(event: ILoggingEvent) {
        events.add(checkNotNull(layout) { "TestAppender requires a layout" }.doLayout(event))
    }

    fun getEvents(): List<String> = events

    fun clear() = events.clear()
}

object LogVerifier {
    private val logger = LoggerFactory.getLogger("auditLog") as Logger

    val appender: TestAppender
        get() = logger.getAppender("auditLogger") as TestAppender

    fun verifyLogCount(expectedSize: Int) {
        val events = appender.getEvents()
        withClue("Log contains the wrong number of events") {
            events.size shouldBe expectedSize
        }
    }

    fun verifyAction(
        action: String,
        expectedAdminIdent: String? = null,
        expectedIdent: String? = null,
        expectedMessage: String? = null,
        expectedJirasak: String? = null,
    ) {
        val events = appender.getEvents()
        val actionEvent = events.firstOrNull { it.contains(action) }
        withClue("Fant ingen auditlog entry for $action") {
            actionEvent shouldNotBe null
        }

        actionEvent.let { auditlogMessage ->
            withClue("Feil i auditlog format") {
                if (expectedAdminIdent != null) {
                    auditlogMessage shouldContain "suid=$expectedAdminIdent"
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

