package no.nav.tms.varsel.authority.read

import kotliquery.Row
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.action.Varseltype.Innboks
import no.nav.tms.varsel.action.Varseltype.Oppgave
import no.nav.tms.varsel.authority.EksternFeilHistorikkEntry
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.Innhold
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object FieldDescription {
    val type = "Type varsel, kan være beskjed, oppgave eller melding"
    val varselId = "Unik identifikator for varselet"
    val aktiv =
        "Om varselet er aktivt, altså ligger under aktive varsler i varselbjelle menyen, eller inaktivt"
    val produsertAv =
        "Hvilken applikasjon som har produsert varselet. Inkluderer også team for nyere varsler"
    val tilgangstyring =
        """Hvilken sensitivtet varselet har blitt gitt. Kan være sikkerhetsnivå eller idporten level of assurance(loa). 
                |Dette vil påvirke muligheten for å se innhold i varsel. 
                |Hvis bruker logger inn med en annen metode enn BankId(f.eks minID) skjules teksten for varsler på sikkerhetsnivå 4 og loa high """.trimMargin()
    val tekst = "Teksten i varslet som vises på NAVs innloggede sider"
    val eksternVarsling = mapOf(
        "sendt" to "Hvorvidt en sms/epost har blitt sendt til bruker i sammenheng med varselt",
        "kanaler" to "Hvilken kanal varslet ble send på, enten sms eller epost",
        "tilleggsopplysninger" to "Annen informasjon vi har som sms-en/ eposten som ble sendt. Hvor mye informasjon avhenger av alder på varslet "
    )
    val lenke = "Lenke som brukeren blir sendt til når de klikker på varselet"
    val eksternVarslingSend = "Hvorvidt ett eksternt varsel (SMS/EPOST) har blitt sendt"
    val eksternVarslingKanaler = "Hvilke kanaler det eksterne varselet er sendt på"
    val eksternVarslingTilleggsinformasjon =
        "Hvorvidt ett varsel er sendt som batch, re-notifikasjon er sendt og feilhistorikk. Data for dette er kun tilgjengelig for nyere varsler"
    val inaktivert =
        "Kilde og tidspunkt for når varselet ble inaktivert, eller 'Ikke inaktivert' hvis varselet er aktivt. For eldre varsel kan dette feltet være tomt selv om varselet er inaktivt"
    val arkivert = "Om varselet er arkivert eller ikke"
    val opprettet = "Dato for oppretting av varslet"
}

data class DetaljertAdminVarsel(
    val type: Varseltype,
    val varselId: String,
    val aktiv: Boolean,
    val produsertAv: String,
    val tilgangstyring: String,
    val innhold: Innhold,
    val eksternVarsling: EksternVarslingInfo?,
    val opprettet: ZonedDateTime,
    val inaktivert: String?,
    val arkivert: Boolean,
) {
    companion object {

        fun Row.resolveInaktivert(varselType: Varseltype): String? {

            val inaktivertAv = stringOrNull("inaktivertAv")
            val inaktivertTidspunkt = zonedDateTimeOrNull("inaktivert")
            val aktiv = this.boolean("aktiv")
            val isLegacy = this.booleanOrNull("fromLegacyJson") ?: false
            val fristUtløpt = this.stringOrNull("fristUtlopt").toBoolean()

            return when {
                aktiv -> "Ikke inaktivert"
                !isLegacy -> {
                    if (inaktivertAv == null && inaktivertTidspunkt == null)
                        "Ikke inaktivert"
                    else
                        "${inaktivertTidspunkt?.dateTimeAndOsloTimesone() ?: ""} ${inaktivertAv?.let { "av ${inaktivertAv.lowercase()}" } ?: "av ukjent kilde"}"
                }

                fristUtløpt -> "av system (frist utløpt)"
                varselType == Innboks -> "av system"
                varselType == Beskjed -> "av bruker/produsent"
                varselType == Oppgave -> "av produsent"
                else -> {
                    "av ukjent kilde"
                }
            }

        }
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
                        }
                } else it
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
    }
}

private fun ZonedDateTime.dateTimeAndOsloTimesone(): String = withZoneSameInstant(ZoneId.of("Europe/Oslo"))
    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy 'kl' HH:mm '(UTC'XXX')'"))

fun Row.resolveTilgangstyring(): String =
    intOrNull("sikkerhetsnivaa")?.let { "Sikkerhetsnivå $it" }
        ?: string("sensitivitet").let { "Idporten level of assurance $it" }

class ArchivedAndCurrentVarsler(
    val varsler: List<DetaljertAdminVarsel>,
    val feilendeVarsler: List<String>,
) {
    val fieldDescription = FieldDescription
}
