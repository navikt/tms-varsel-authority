package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.LocalDateTimeHelper
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import java.time.LocalDateTime
import java.time.ZonedDateTime

fun aktiverVarselEvent(
    type: String,
    varselId: String,
    produsent: DatabaseProdusent = DatabaseProdusent(null, "namespace", "appnavn"),
    fodselsnummer: String = "01234567890",
    tekst: String = "tekst",
    link: String = "http://link",
    sikkerhetsnivaa: Int = 4,
    forstBehandlet: LocalDateTime = LocalDateTimeHelper.nowAtUtc(),
    synligFremTil: LocalDateTime = LocalDateTimeHelper.nowAtUtc().plusDays(1),
    aktiv: Boolean = true,
    eksternVarsling: Boolean = true,
    prefererteKanaler: List<String> = listOf("SMS", "EPOST")
) = VarselEvent (
    eventName = type,
    eventId = varselId,
    namespace = produsent.namespace,
    appnavn = produsent.appnavn,
    fodselsnummer = fodselsnummer,
    tekst = tekst,
    link = link,
    sikkerhetsnivaa = sikkerhetsnivaa,
    forstBehandlet = forstBehandlet,
    synligFremTil = synligFremTil,
    aktiv = aktiv,
    eksternVarsling = eksternVarsling,
    prefererteKanaler = prefererteKanaler
)

data class VarselEvent(
    @JsonProperty("@event_name") val eventName: String,
    val eventId: String,
    val namespace: String,
    val appnavn: String,
    val fodselsnummer: String,
    val tekst: String,
    val link: String,
    val sikkerhetsnivaa: Int,
    val forstBehandlet: LocalDateTime,
    val synligFremTil: LocalDateTime,
    val aktiv: Boolean,
    val eksternVarsling: Boolean,
    val prefererteKanaler: List<String>
)

fun inaktiverVarselEvent(varselId: String) = InaktiverVarselEvent(eventId = varselId)

data class InaktiverVarselEvent(
    @JsonProperty("@event_name") val eventName: String = "done",
    val eventId: String
)

fun opprettVarselEvent(
    type: String,
    varselId: String,
    produsent: Produsent = Produsent("cluster", "namespace", "appnavn"),
    ident: String = "01234567890",
    tekster: List<Tekst> = listOf(Tekst("no", "tekst", true)),
    link: String? = "http://link",
    sensitivitet: Sensitivitet = Sensitivitet.High,
    aktivFremTil: ZonedDateTime = nowAtUtc().plusDays(1),
    eksternVarsling: EksternVarslingBestilling? = EksternVarslingBestilling(
        prefererteKanaler = listOf(EksternKanal.SMS, EksternKanal.EPOST),
        smsVarslingstekst = "smsTekst",
        epostVarslingstittel = null,
        epostVarslingstekst = null
    )
) = """
{
    "@event_name": "opprett",
    "type": "$type",
    "varselId": "$varselId",
    "produsent": {
        "cluster": "${produsent.cluster}",
        "namespace": "${produsent.namespace}",
        "appnavn": "${produsent.appnavn}"
    },
    "ident": "$ident",
    "tekster": ${tekster(tekster)},
    "link": ${link.asJson()},
    "sensitivitet": "${sensitivitet.name.lowercase()}",
    "aktivFremTil": "$aktivFremTil",
    "eksternVarsling": ${eksternVarsling(eksternVarsling)},
    "metadata": {
        "built_at": "${nowAtUtc()}",
        "version": "test"
    }
}
"""

private fun tekster(tekster: List<Tekst>): String = tekster.joinToString(prefix = "[", postfix = "]") {
    """
{
    "spraakkode": "${it.spraakkode}",
    "tekst": "${it.tekst}",
    "default": ${it.default}
} 
    """
}

private fun eksternVarsling(eksternVarsling: EksternVarslingBestilling?): String {
    return if (eksternVarsling != null) {
    """
{
    "prefererteKanaler": [${eksternVarsling.prefererteKanaler.joinToString(",") { "\"${it.name}\"" }}],
    "smsVarslingstekst": ${ eksternVarsling.smsVarslingstekst.asJson() },
    "epostVarslingstekst": ${ eksternVarsling.epostVarslingstekst.asJson() },
    "epostVarslingstittel": ${ eksternVarsling.epostVarslingstittel.asJson() }
} 
    """
    } else {
        "null"
    }
}

private fun String?.asJson() = if (this == null) {
    "null"
} else {
    "\"$this\""
}
