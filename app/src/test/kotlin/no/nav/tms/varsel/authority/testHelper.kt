package no.nav.tms.varsel.authority

import io.kotest.matchers.date.shouldHaveSameInstantAs
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object LocalDateTimeHelper {
    fun nowAtUtc() = LocalDateTime.now((ZoneId.of("UTC"))).truncatedTo(ChronoUnit.MILLIS)
}

fun mockProducer() = MockProducer(
    false,
    null,
    StringSerializer(),
    StringSerializer()
)

fun Any?.optionalJson(name: String, isEnd: Boolean = false): String {
    val suffix = if(isEnd) "" else ","

    return when (this) {
        null -> ""
        is String -> "\"$name\": \"$this\"$suffix"
        is Number -> "\"$name\": $this$suffix"
        is Boolean -> "\"$name\": $this$suffix"
        else -> throw IllegalArgumentException("Not supported")
    }
}
infix fun String.shouldBeSameTime(other: String) {
    val left = try {
        ZonedDateTime.parse(this)

    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Left hand side did not parse as zoned datetime")
    }

    val right = try {
        ZonedDateTime.parse(other)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("Left hand side did not parse as zoned datetime")
    }

    left.shouldHaveSameInstantAs(right)
}
