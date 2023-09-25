package no.nav.tms.varsel.authority

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val objectMapper = defaultObjectMapper()

private val nullObjectMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

fun Any.toJson(includeNull: Boolean = false) = if(includeNull){
    nullObjectMapper.writeValueAsString(this)
} else {
    objectMapper.writeValueAsString(this)
}

object LocalDateTimeHelper {
    fun nowAtUtc() = LocalDateTime.now((ZoneId.of("UTC"))).truncatedTo(ChronoUnit.MILLIS)
}
