package no.nav.tms.varsel.authority.write.opprett

import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import java.time.ZonedDateTime

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
        epostVarslingstekst = null,
        kanBatches = true,
        utsettSendingTil = nowAtUtc().plusDays(7),
    ),
    ekstraMetadada: String=""
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
        $ekstraMetadada
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
    "epostVarslingstittel": ${ eksternVarsling.epostVarslingstittel.asJson() },
    "epostVarslingstekst": ${ eksternVarsling.epostVarslingstekst.asJson() },
    "kanBatches": ${eksternVarsling.kanBatches.asJson()},
    "utsettSendingTil": ${eksternVarsling.utsettSendingTil.asJson()}

} 
    """
    } else {
        "null"
    }
}

private fun Any?.asJson() = if (this == null) {
    "null"
} else {
    "\"$this\""
}

