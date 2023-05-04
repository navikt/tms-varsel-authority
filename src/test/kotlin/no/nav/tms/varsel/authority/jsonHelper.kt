package no.nav.tms.varsel.authority

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder

private val objectMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)

private val nullObjectMapper = jacksonMapperBuilder()
    .addModule(JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .build()

fun Any.toJson(includeNull: Boolean = false) = if(includeNull){
    nullObjectMapper.writeValueAsString(this)
} else {
    objectMapper.writeValueAsString(this)
}
