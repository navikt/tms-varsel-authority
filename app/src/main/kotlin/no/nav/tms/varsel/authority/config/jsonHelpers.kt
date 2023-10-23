package no.nav.tms.varsel.authority.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import no.nav.helse.rapids_rivers.JsonMessage

fun defaultObjectMapper() = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

val JsonMessage.rawJson: JsonNode get() =
    javaClass.getDeclaredField("json").let {
        it.isAccessible = true
        it.get(this) as JsonNode
    }

