package no.nav.tms.varsel.authority

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object LocalDateTimeHelper {
    fun nowAtUtc() = LocalDateTime.now((ZoneId.of("UTC"))).truncatedTo(ChronoUnit.MILLIS)
}

fun Any?.optionalJson(name: String, isEnd: Boolean = false): String {
    val suffix = if(isEnd) "" else ","

    return when (this) {
        null -> ""
        is String -> "\"$name\": \"$this\"$suffix"
        is Number -> "\"$name\": $this$suffix"
        else -> throw IllegalArgumentException("Not supported")
    }
}
