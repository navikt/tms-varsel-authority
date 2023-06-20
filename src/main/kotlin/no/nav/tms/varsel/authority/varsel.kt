package no.nav.tms.varsel.authority

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import java.time.ZonedDateTime

data class DatabaseVarsel(
    val type: VarselType,
    val varselId: String,
    val ident: String,
    val aktiv: Boolean,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val produsent: Produsent,
    val eksternVarslingBestilling: EksternVarslingBestilling? = null,
    val eksternVarslingStatus: EksternVarslingStatus? = null,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime? = null,
    val inaktivert: ZonedDateTime? = null,
    val inaktivertAv: VarselInaktivertKilde? = null
)

data class Innhold(
    val tekst: String,
    val link: String?
)

enum class VarselType {
    Beskjed, Oppgave, Innboks;

    val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName

    companion object {
        fun parse(string: String): VarselType {
            return values()
                .filter { it.lowercaseName == string.lowercase() }
                .firstOrNull() ?: throw IllegalArgumentException("Could not parse varselType $string")
        }
    }
}

enum class Sensitivitet {
    Substantial,
    High;

    val lowercaseName = name.lowercase()

    @JsonValue
    fun toJson() = lowercaseName

    companion object {
        fun parse(string: String): Sensitivitet {
            return Sensitivitet.values()
                .filter { it.lowercaseName == string.lowercase() }
                .firstOrNull() ?: throw IllegalArgumentException("Could not parse sensitivitet $string")
        }
    }
}

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
    val sendt: Boolean,
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
