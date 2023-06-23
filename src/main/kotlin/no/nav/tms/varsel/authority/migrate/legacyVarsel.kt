package no.nav.tms.varsel.authority.migrate

import io.ktor.util.*
import no.nav.tms.varsel.authority.*
import java.time.ZonedDateTime


data class LegacyVarsel(
    val type: VarselType,
    val fodselsnummer: String,
    val eventId: String,
    val aktiv: Boolean,
    val tekst: String,
    val link: String,
    val sikkerhetsnivaa: Int,
    val synligFremTil: ZonedDateTime?,
    val namespace: String,
    val appnavn: String,
    val forstBehandlet: ZonedDateTime,
    val eksternVarsling: Boolean,
    val prefererteKanaler: List<String>,
    val eksternVarslingStatus: EksternVarslingStatus?,
    val sistOppdatert: ZonedDateTime,
    val fristUtlopt: Boolean
) {
    val sensitivitet = when(sikkerhetsnivaa) {
        3 -> Sensitivitet.Substantial
        else -> Sensitivitet.High
    }

    fun mapInnhold() = Innhold(
        tekst = tekst,
        link = link
    )

    fun mapProdusent() = Produsent(
        namespace = namespace,
        appnavn = appnavn
    )

    fun mapEksternVarslingBestilling() = EksternVarslingBestilling(
        prefererteKanaler = prefererteKanaler,
        smsVarslingstekst = null,
        epostVarslingstekst = null,
        epostVarslingstittel = null
    )
}

data class LegacyArkivertVarsel(
    val type: VarselType,
    val eventId: String,
    val fodselsnummer: String,
    val tekst: String,
    val link: String,
    val sikkerhetsnivaa: Int,
    val aktiv: Boolean,
    val produsentApp: String,
    val eksternVarslingSendt: Boolean,
    val eksternVarslingKanaler: List<String>,
    val forstBehandlet: ZonedDateTime,
    val arkivert: ZonedDateTime,
    val fristUtlopt: Boolean
)
