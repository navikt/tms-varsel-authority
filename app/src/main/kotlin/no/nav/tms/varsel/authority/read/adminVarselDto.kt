package no.nav.tms.varsel.authority.read

import kotliquery.Row
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.EksternFeilHistorikkEntry
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.Innhold
import java.time.ZonedDateTime

data class DetaljertAdminVarsel(
    val type: Varseltype,
    val varselId: String,
    val aktiv: Boolean,
    val produsertAv: String,
    val tilgangstyring: String,
    val innhold: Innhold,
    val eksternVarsling: EksternVarslingInfo?,
    val opprettet: ZonedDateTime,
    val inaktivert: String,
    val arkivert: Boolean,
)

data class EksternVarslingInfo(
    val sendt: Boolean,
    val kanaler: List<String>,
    val tilleggsooplysninger: List<String>
)

data class EksternVarslingArchiveCompatible(
    val sendt: Boolean,
    val sendtSomBatch: Boolean?,
    val renotifikasjonSendt: Boolean?,
    val kanaler: List<String>,
    val feilhistorikk: List<EksternFeilHistorikkEntry>? = emptyList(),
    val sisteStatus: EksternStatus? = null,
    val sistOppdatert: ZonedDateTime?
) {
    private fun tilleggsooplysninger(): MutableList<String> {
        val opplysninger = mutableListOf<String>()
        opplysninger.add("Siste oppdtaering: $sistOppdatert")
        opplysninger.addIf(sendtSomBatch != null) { "Sendt som batch" }
        opplysninger.addIf(renotifikasjonSendt != null) { "Re-notifikasjon sendt" }
        opplysninger.addIf(feilhistorikk != null && feilhistorikk.isNotEmpty()) {
            val sisteFeil = feilhistorikk!!.last()
            "${feilhistorikk.size} oppføringer i feilhistorikk.\nSiste feil: ${sisteFeil.feilmelding} den ${sisteFeil.tidspunkt}"
        }
        opplysninger.addIf(sisteStatus != null) { "Siste status ${sisteStatus!!.name}" }
        return opplysninger
    }

    fun toEksternVarslingInfo(): EksternVarslingInfo =
        EksternVarslingInfo(
            sendt = this.sendt,
            kanaler = this.kanaler,
            tilleggsooplysninger = this.tilleggsooplysninger()
        )

    companion object {
        private fun MutableList<String>.addIf(condition: Boolean, stringproducer: () -> String) {
            if (condition) {
                this.add(stringproducer())
            }
        }
    }
}


fun Row.resolveTilgangstyring(): String =
    intOrNull("sikkerhetsnivaa")?.let { "Sikkerhetsnivå $it" }
        ?: string("sensitivitet").let { "Idporten level of assurance $it" }