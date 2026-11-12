package no.nav.tms.varsel.authority

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.date.shouldHaveSameInstantAs
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException


fun mockProducer(autoComplete: Boolean = true) = MockProducer(
    autoComplete,
    null,
    StringSerializer(),
    StringSerializer()
)

private val objectMapper = jacksonObjectMapper()

fun Any?.optionalJson(name: String, isEnd: Boolean = false): String {
    val suffix = if(isEnd) "" else ","

    return when (this) {
        null -> ""
        is String -> "\"$name\": \"$this\"$suffix"
        is Number -> "\"$name\": $this$suffix"
        is Boolean -> "\"$name\": $this$suffix"
        else -> "\"$name\": ${objectMapper.writeValueAsString(this)}$suffix"
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
