package no.nav.tms.varsel.authority

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import no.nav.tms.varsel.action.*
import java.time.ZonedDateTime

data class DatabaseVarsel(
    val type: Varseltype,
    val varselId: String,
    val ident: String,
    val aktiv: Boolean,
    val sensitivitet: Sensitivitet,
    val innhold: Innhold,
    val produsent: DatabaseProdusent,
    val eksternVarslingBestilling: EksternVarslingBestilling? = null,
    val eksternVarslingStatus: EksternVarslingStatus? = null,
    val opprettet: ZonedDateTime,
    val aktivFremTil: ZonedDateTime? = null,
    val inaktivert: ZonedDateTime? = null,
    val inaktivertAv: VarselInaktivertKilde? = null,
    val metadata: Map<String, Any>? = null
)

data class Innhold(
    val tekst: String,
    val link: String?,
    val tekster: List<Tekst> = emptyList()
)

data class DatabaseProdusent(
    val cluster: String?,
    val namespace: String,
    val appnavn: String
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
