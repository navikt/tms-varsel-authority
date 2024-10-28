package no.nav.tms.varsel.authority.write.eksternvarsling

import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.LocalDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import no.nav.tms.varsel.authority.optionalJson
import java.time.LocalDateTime
import java.time.ZonedDateTime

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

fun eksternVarslingOppdatert(
    status: EksternStatus,
    varselId: String,
    kanal: String?,
    renotifikasjon: Boolean?,
    batch: Boolean?,
    feilmelding: String?,
    tidspunkt: ZonedDateTime = ZonedDateTimeHelper.nowAtUtc()
) = """
{
    "@event_name": "eksternVarslingStatusOppdatert",
    "status": "${status.lowercaseName}",
    "varselId": "$varselId",
    ${kanal.optionalJson("kanal")}
    ${renotifikasjon.optionalJson("renotifikasjon")}
    ${batch.optionalJson("batch")}
    ${feilmelding.optionalJson("feilmelding")}
    "tidspunkt": "$tidspunkt" 
}
"""
