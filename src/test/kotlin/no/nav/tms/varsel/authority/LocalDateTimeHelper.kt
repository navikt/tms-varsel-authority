package no.nav.tms.varsel.authority

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object LocalDateTimeHelper {
    fun nowAtUtc() = LocalDateTime.now((ZoneId.of("UTC"))).truncatedTo(ChronoUnit.MILLIS)
}
