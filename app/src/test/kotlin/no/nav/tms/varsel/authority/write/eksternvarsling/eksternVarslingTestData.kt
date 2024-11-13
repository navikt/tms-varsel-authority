package no.nav.tms.varsel.authority.write.eksternvarsling

import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper
import no.nav.tms.varsel.authority.optionalJson
import java.time.ZonedDateTime

fun eksternVarslingOppdatert(
    status: EksternStatus,
    varselId: String,
    kanal: String? = null,
    renotifikasjon: Boolean? = null,
    batch: Boolean? = null,
    feilmelding: String? = null,
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
