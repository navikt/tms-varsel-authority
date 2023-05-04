package no.nav.tms.varsel.authority.common

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object ZonedDateTimeHelper {
    fun nowAtUtc(): ZonedDateTime = ZonedDateTime.now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS)

    fun JsonNode.asZonedDateTime(): ZonedDateTime {
        return parseZonedDateTimeDefaultUtc(asText())
    }

    fun JsonNode.asOptionalZonedDateTime(): ZonedDateTime? {
        return takeIf(JsonNode::isTextual)
            ?.asText()
            ?.takeIf(String::isNotEmpty)
            ?.let { parseZonedDateTimeDefaultUtc(it) }
    }



    private fun parseZonedDateTimeDefaultUtc(dateTimeString: String): ZonedDateTime {
        return try {
            return ZonedDateTime.parse(dateTimeString)
        } catch (e: DateTimeParseException) {
            LocalDateTime.parse(dateTimeString).atZone(ZoneId.of("UTC"))
        }
    }
}

