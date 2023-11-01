package no.nav.tms.varsel.authority.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype
import org.postgresql.util.PGobject


inline fun <reified T> Row.json(label: String, objectMapper: ObjectMapper = defaultObjectMapper()): T {
    return objectMapper.readValue(string(label))
}

inline fun <reified T> Row.optionalJson(label: String, objectMapper: ObjectMapper = defaultObjectMapper()): T? {
    val jsonString = stringOrNull(label)

    return jsonString?.let { objectMapper.readValue(jsonString) }
}

fun Any?.toJsonb(objectMapper: ObjectMapper = defaultObjectMapper()): PGobject? {
    return if (this == null) {
        null
    } else {
        objectMapper.writeValueAsString(this).let {
            PGobject().apply {
                type = "jsonb"
                value = it
            }
        }
    }
}

fun parseSensitivitet(string: String): Sensitivitet {
    return Sensitivitet.values()
        .filter { it.name.lowercase() == string.lowercase() }
        .firstOrNull() ?: throw IllegalArgumentException("Could not parse sensitivitet $string")
}

fun parseVarseltype(string: String): Varseltype {
    return Varseltype.values()
        .filter { it.name.lowercase() == string.lowercase() }
        .firstOrNull() ?: throw IllegalArgumentException("Could not parse varseltype $string")
}
