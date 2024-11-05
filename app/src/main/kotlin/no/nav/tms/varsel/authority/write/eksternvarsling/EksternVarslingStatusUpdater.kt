package no.nav.tms.varsel.authority.write.eksternvarsling

import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.EksternStatus.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.slf4j.MDC

class EksternVarslingStatusUpdater(
    private val eksternVarslingStatusRepository: EksternVarslingStatusRepository,
    private val varselRepository: WriteVarselRepository
) {

    fun updateEksternVarslingStatus(statusEvent: EksternVarslingOppdatert) {
        val varsel = varselRepository.getVarsel(statusEvent.varselId)

        if (varsel == null) {
            return
        }

        MDC.put("type", varsel.type.name.lowercase())

        val currentStatus = varsel.eksternVarslingStatus ?: emptyEksternVarsling()

        val feilhistorikk = if (statusEvent.feilmelding != null) {
            EksternFeilHistorikkEntry(
                feilmelding = statusEvent.feilmelding,
                tidspunkt = statusEvent.tidspunkt,
            ).let {
                currentStatus.feilhistorikk + it
            }.distinct()
        } else {
            currentStatus.feilhistorikk
        }

        val updatedStatus = EksternVarslingStatus(
            sendt = currentStatus.sendt || statusEvent.status == Sendt,
            sendtSomBatch = currentStatus.sendtSomBatch || statusEvent.batch == true,
            renotifikasjonSendt = if (statusEvent.renotifikasjon == true) true else currentStatus.renotifikasjonSendt,
            kanaler = (currentStatus.kanaler + statusEvent.kanal).filterNotNull().distinct(),
            historikk = currentStatus.historikk,
            feilhistorikk = feilhistorikk,
            sistOppdatert = nowAtUtc()
        )

        eksternVarslingStatusRepository.updateEksternVarslingStatus(varsel.varselId, updatedStatus)
    }



    private fun emptyEksternVarsling() = EksternVarslingStatus(
        sendt = false,
        renotifikasjonSendt = false,
        kanaler = emptyList(),
        historikk = emptyList(),
        sistOppdatert = nowAtUtc()
    )
}
