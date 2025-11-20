package no.nav.tms.varsel.authority.read

import kotliquery.Row
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.EksternFeilHistorikkEntry
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.Innhold
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Locale.getDefault

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
) {
    companion object {
        val typeDescription = "Type varsel, kan være beskjed, oppgave eller melding"
        val varselIdDescription = "Unik identifikator for varselet"
        val aktivDescription =
            "Om varselet er aktivt, altså ligger under aktive varsler i varselbjelle menyen, eller inaktivt"
        val produsertAvDescription =
            "Hvilken applikasjon som har produsert varselet. Inkluderer også team for nyere varsler"
        val tilgangstyringDescription =
            """Hvilken sensitivtet varselet har blitt gitt. Kan være sikkerhetsnivå eller idporten level of assurance(loa). 
                |Dette vil påvirke muligheten for å se innhold i varsel. 
                |Hvis bruker logger inn med en annen metode enn BankId(f.eks minID) skjules teksten for varsler på sikkerhetsnivå 4 og loa high """.trimMargin()
        val tekstDescription = "Teksten i varslet som vises på NAVs innloggede sider"
        val lenkeDescription = "Lenke som brukeren blir sendt til når de klikker på varselet"
        val eksternVarslingSendDescription = "Hvorvidt ett eksternt varsel (SMS/EPOST) har blitt sendt"
        val eksternVarslingKanalerDescription = "Hvilke kanaler det eksterne varselet er sendt på"
        val eksternVarslingTilleggsinformasjonDescription =
            "Hvorvidt ett varsel er sendt som batch, re-notifikasjon er sendt og feilhistorikk. Data for dette er kun tilgjengelig for nyere varsler"
        val inaktivertDescription =
            "Årsak og tidspunkt for når varselet ble inaktivert, eller 'Ikke inaktivert' hvis varselet er aktivt. For eldre varsel kan dette feltet være tomt selv om varselet er inaktivt"
        val arkivertDescription = "Om varselet er arkivert eller ikke"
    }
}

data class EksternVarslingInfo(
    val sendt: Boolean,
    val kanaler: List<String>,
    val tilleggsopplysninger: List<String>
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
    private fun tilleggsopplysninger(): MutableList<String> {
        val opplysninger = mutableListOf<String>()
        opplysninger.addIf(sistOppdatert != null) {
            "Siste oppdatering: ${
                sistOppdatert!!.dateTimeAndOsloTimesone()
            }"
        }
        opplysninger.addIf(sendtSomBatch != null) { "Sendt som batch" }
        opplysninger.addIf(renotifikasjonSendt != null) { "Re-notifikasjon sendt" }
        opplysninger.addIf(feilhistorikk != null) {
            "${feilhistorikk!!.size} oppføringer i feilhistorikk".let {
                if (feilhistorikk.isNotEmpty()) {
                feilhistorikk
                    .sortedBy { entry -> entry.tidspunkt }
                    .joinToString("\n", prefix = "$it:\n", postfix = "\n----------") { entry ->
                    "${entry.tidspunkt.dateTimeAndOsloTimesone()}: ${entry.feilmelding}"
                }} else it
            }
        }
        opplysninger.addIf(sisteStatus != null) { "Siste status: ${sisteStatus!!.name.lowercase()}" }
        return opplysninger
    }

    fun toEksternVarslingInfo(): EksternVarslingInfo =
        EksternVarslingInfo(
            sendt = this.sendt,
            kanaler = this.kanaler,
            tilleggsopplysninger = this.tilleggsopplysninger()
        )

    companion object {
        private fun MutableList<String>.addIf(condition: Boolean, stringproducer: () -> String) {
            if (condition) {
                this.add(stringproducer())
            }
        }

        private fun ZonedDateTime.dateTimeAndOsloTimesone(): String = withZoneSameInstant(ZoneId.of("Europe/Oslo"))
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'kl' HH:mm '(UTC'XXX')'"))
    }
}


fun Row.resolveTilgangstyring(): String =
    intOrNull("sikkerhetsnivaa")?.let { "Sikkerhetsnivå $it" }
        ?: string("sensitivitet").let { "Idporten level of assurance $it" }

class ArchivedAndCurrentVarsler(
    val varsler: List<DetaljertAdminVarsel>,
    val feilendeVarsler: List<String>,
) {
    val fieldDescriptions: Map<String, String> = mapOf(
        "type" to DetaljertAdminVarsel.typeDescription,
        "varselId" to DetaljertAdminVarsel.varselIdDescription,
        "aktiv" to DetaljertAdminVarsel.aktivDescription,
        "produsertAv" to DetaljertAdminVarsel.produsertAvDescription,
        "tilgangstyring" to DetaljertAdminVarsel.tilgangstyringDescription,
        "innhold.tekst" to DetaljertAdminVarsel.tekstDescription,
        "innhold.link" to DetaljertAdminVarsel.lenkeDescription,
        "eksternVarsling.sendt" to DetaljertAdminVarsel.eksternVarslingSendDescription,
        "eksternVarsling.kanaler" to DetaljertAdminVarsel.eksternVarslingKanalerDescription,
        "eksternVarsling.tilleggsooplysninger" to DetaljertAdminVarsel.eksternVarslingTilleggsinformasjonDescription,
        "inaktivert" to DetaljertAdminVarsel.inaktivertDescription,
        "arkivert" to DetaljertAdminVarsel.arkivertDescription,
    )
}
