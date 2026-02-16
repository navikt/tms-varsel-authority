package no.nav.tms.varsel.authority.write.eksternvarsling

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.kafka.application.MessageException
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.EksternStatus.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.slf4j.MDC

class EksternVarslingStatusUpdater(
    private val eksternVarslingStatusRepository: EksternVarslingStatusRepository,
    private val varselRepository: WriteVarselRepository,
    private val feilhistorikkMaxSize: Int = 10
) {
    private val log = KotlinLogging.logger { }

    fun updateEksternVarslingStatus(statusEvent: EksternVarslingOppdatert) {
        val varsel = varselRepository.getVarsel(statusEvent.varselId)

        if (varsel == null) {
            throw UpdatedVarselMissingException()
        }

        MDC.put("type", varsel.type.name.lowercase())

        val currentStatus = varsel.eksternVarslingStatus ?: emptyEksternVarsling()

        val feilhistorikk = if (statusEvent.feilmelding == null) {
            currentStatus.feilhistorikk
        } else if (feilhistorikkIsFull(currentStatus)) {
            log.warn { "Ignorerer feilet-status fordi feilhistorikken er full (max_entries: $feilhistorikkMaxSize)." }
            currentStatus.feilhistorikk
        } else {
            EksternFeilHistorikkEntry(
                feilmelding = statusEvent.feilmelding,
                tidspunkt = statusEvent.tidspunkt,
            ).let {
                currentStatus.feilhistorikk + it
            }.distinct()
        }

        val updatedStatus = EksternVarslingStatus(
            sendt = currentStatus.sendt || statusEvent.status == Sendt,
            sendtSomBatch = currentStatus.sendtSomBatch || (statusEvent.batch == true && statusEvent.status == Sendt),
            sendtTidspunkt = if (statusEvent.status == Sendt && statusEvent.renotifikasjon == false) statusEvent.tidspunkt else currentStatus.sendtTidspunkt,
            renotifikasjonSendt = if (statusEvent.renotifikasjon == true) true else currentStatus.renotifikasjonSendt,
            renotifikasjonTidspunkt = if (statusEvent.renotifikasjon == true) statusEvent.tidspunkt else currentStatus.renotifikasjonTidspunkt,
            kanaler = (currentStatus.kanaler + statusEvent.kanal).filterNotNull().distinct(),
            feilhistorikk = feilhistorikk,
            sisteStatus = statusEvent.status,
            sistOppdatert = nowAtUtc()
        )

        eksternVarslingStatusRepository.updateEksternVarslingStatus(varsel.varselId, updatedStatus)
    }


    private fun emptyEksternVarsling() = EksternVarslingStatus(
        sendt = false,
        renotifikasjonSendt = false,
        kanaler = emptyList(),
        sistOppdatert = nowAtUtc()
    )

    private fun feilhistorikkIsFull(status: EksternVarslingStatus): Boolean {
        return status.feilhistorikk.size >= feilhistorikkMaxSize
    }
}

class UpdatedVarselMissingException : MessageException("Fant ikke varsel tilhørende ekstern varseloppdatering")
