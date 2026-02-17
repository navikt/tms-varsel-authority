package no.nav.tms.varsel.authority.write.eksternvarsling

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import no.nav.tms.kafka.application.MessageException
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.EksternStatus.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import kotlin.collections.distinct
import kotlin.collections.plus

class EksternVarslingStatusUpdater(
    private val eksternVarslingStatusRepository: EksternVarslingStatusRepository,
    private val varselRepository: WriteVarselRepository,
    private val feilhistorikkMaxSize: Int = 10
) {
    private val log = KotlinLogging.logger { }

    fun updateEksternVarslingStatus(statusEvent: EksternVarslingOppdatert) {
        val varsel = varselRepository.getVarsel(statusEvent.varselId)

        if (varsel == null) {
            log.warn { "Ignorerer status [${statusEvent.status}] fordi tilhørende varsel ikke fantes." }
            throw UpdatedVarselMissingException()
        } else if (statusEvent.feilmelding != null && feilhistorikkIsFull(varsel.eksternVarslingStatus)) {
            log.warn { "Ignorerer feilet-status fordi feilhistorikken er full (max_entries: $feilhistorikkMaxSize)." }
            throw FeilhistorikkFullException()
        }

        val currentStatus = varsel.eksternVarslingStatus ?: emptyEksternVarsling()

        withLoggingContext("type" to varsel.type.name.lowercase()) {
            mapStatusAndPerformUpdate(varsel.varselId, currentStatus, statusEvent)
        }
    }

    private fun mapStatusAndPerformUpdate(varselId: String, currentStatus: EksternVarslingStatus, statusEvent: EksternVarslingOppdatert) {
        val feilhistorikk = if (statusEvent.feilmelding == null) {
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

        eksternVarslingStatusRepository.updateEksternVarslingStatus(varselId, updatedStatus)
    }

    private fun emptyEksternVarsling() = EksternVarslingStatus(
        sendt = false,
        renotifikasjonSendt = false,
        kanaler = emptyList(),
        sistOppdatert = nowAtUtc()
    )

    private fun feilhistorikkIsFull(status: EksternVarslingStatus?): Boolean {
        return status?.feilhistorikk != null && status.feilhistorikk.size >= feilhistorikkMaxSize
    }
}

class UpdatedVarselMissingException : MessageException("Fant ikke varsel tilhørende ekstern varseloppdatering")
class FeilhistorikkFullException: MessageException("Ignorerer feil-status; Feilhistorikken er full")
