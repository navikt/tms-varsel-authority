package no.nav.tms.varsel.authority.sink

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.tms.varsel.authority.done.VarselInaktivertKilde
import java.time.ZonedDateTime

data class DatabaseVarsel(
    val aktiv: Boolean,
    val varsel: Varsel,
    val produsent: Produsent,
    val eksternVarslingBestilling: EksternVarslingBestilling? = null,
    val eksternVarslingStatus: EksternVarslingStatus? = null,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime? = null,
    val inaktivert: ZonedDateTime? = null,
    val inaktivertAv: VarselInaktivertKilde? = null
) {
    val type get() = varsel.type
    val varselId get() = varsel.varselId
    val ident get() = varsel.ident
}

data class Varsel(
    val type: String,
    val varselId: String,
    val ident: String,
    val sikkerhetsnivaa: Int,
    val tekst: String,
    val link: String,
)

data class Produsent(
    val namespace: String,
    val appnavn: String
)

data class EksternVarslingBestilling(
    val prefererteKanaler: List<String>,
    val smsVarslingstekst: String?,
    val epostVarslingstekst: String?,
    val epostVarslingstittel: String?,
)

data class EksternVarslingStatus(
    val eksternVarslingSendt: Boolean,
    val renotifikasjonSendt: Boolean,
    val kanaler: List<String>,
    val historikk: List<EksternVarslingHistorikkEntry>,
    val sistOppdatert: ZonedDateTime
)

data class EksternVarslingHistorikkEntry(
    val melding: String,
    val status: EksternStatus,
    val distribusjonsId: Long?,
    val kanal: String?,
    val renotifikasjon: Boolean?,
    val tidspunkt: ZonedDateTime
)

enum class EksternStatus {
    Feilet, Info, Bestilt, Sendt, Ferdigstilt;

    val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName
}
