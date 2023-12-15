package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.annotation.JsonValue

enum class VarselInaktivertKilde {
    Bruker, Produsent, Frist, Admin;

    @JsonValue
    val lowercaseName = name.lowercase()

    companion object {
        fun from(string: String): VarselInaktivertKilde {
            return values().filter { it.lowercaseName == string.lowercase() }
                .takeIf { it.size == 1 }
                ?.first()
                ?: throw IllegalArgumentException("No VarselInaktivertKilde enum matches $string")
        }
    }
}
