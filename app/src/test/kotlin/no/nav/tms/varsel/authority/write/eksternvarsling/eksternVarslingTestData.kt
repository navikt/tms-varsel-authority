package no.nav.tms.varsel.authority.write.eksternvarsling

import no.nav.tms.varsel.authority.LocalDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.optionalJson
import java.time.LocalDateTime

fun eksternVarslingStatus(
    varselId: String,
    status: DoknotifikasjonStatusEnum = DoknotifikasjonStatusEnum.INFO,
    bestillerAppnavn: String  = "appnavn",
    melding: String = "Melding",
    distribusjonsId: Long? = null,
    kanal: String? = null,
    tidspunkt: LocalDateTime = nowAtUtc()
) = """
{
    "@event_name": "eksternVarslingStatus",
    "eventId": "$varselId",
    "bestillerAppnavn": "$bestillerAppnavn",
    "status": "$status",
    "melding": "$melding",
    ${distribusjonsId.optionalJson("distribusjonsId")}
    ${kanal.optionalJson("kanal")}
    "tidspunkt": "$tidspunkt"
}
"""
