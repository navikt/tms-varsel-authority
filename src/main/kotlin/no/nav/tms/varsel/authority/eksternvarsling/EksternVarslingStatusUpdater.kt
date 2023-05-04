package no.nav.tms.varsel.authority.eksternvarsling

import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.eksternvarsling.DoknotifikasjonStatusEnum.*
import no.nav.tms.varsel.authority.sink.*
import no.nav.tms.varsel.authority.sink.EksternStatus.*
import java.time.Duration
import java.time.temporal.ChronoUnit

class EksternVarslingStatusUpdater(
    private val eksternVarslingStatusRepository: EksternVarslingStatusRepository,
    private val varselRepository: VarselRepository,
    private val eksternVarslingOppdatertProducer: EksternVarslingOppdatertProducer
) {

    fun updateEksternVarslingStatus(statusEvent: DoknotifikasjonStatusEvent) {
        val varsel = varselRepository.getVarsel(statusEvent.eventId)

        if (varsel == null || statusIsDuplicate(varsel, statusEvent)) {
            return
        }

        val currentStatus = varsel.eksternVarslingStatus ?: initializeEksternVarsling()

        updateExistingStatus(statusEvent, currentStatus, varsel)
    }

    private fun initializeEksternVarsling() = EksternVarslingStatus(
        eksternVarslingSendt = false,
        renotifikasjonSendt = false,
        kanaler = emptyList(),
        historikk = emptyList(),
        sistOppdatert = nowAtUtc()
    )

    private fun updateExistingStatus(statusEvent: DoknotifikasjonStatusEvent, currentStatus: EksternVarslingStatus, varsel: DatabaseVarsel) {
        val newEntry = EksternVarslingHistorikkEntry(
            melding = statusEvent.melding,
            status = determineInternalStatus(statusEvent),
            distribusjonsId = statusEvent.distribusjonsId,
            kanal = statusEvent.kanal,
            renotifikasjon = determineIfRenotifikasjon(currentStatus, statusEvent),
            tidspunkt = statusEvent.tidspunkt
        )

        val updatedStatus = EksternVarslingStatus(
            eksternVarslingSendt = currentStatus.eksternVarslingSendt || newEntry.status == Sendt,
            renotifikasjonSendt = if (newEntry.renotifikasjon == true) true else currentStatus.renotifikasjonSendt,
            kanaler = (currentStatus.kanaler + newEntry.kanal).filterNotNull().distinct(),
            historikk = currentStatus.historikk + newEntry,
            sistOppdatert = nowAtUtc()
        )

        eksternVarslingStatusRepository.updateEksternVarslingStatus(varsel.varselId, updatedStatus)

        eksternVarslingOppdatertProducer.eksternStatusOppdatert(buildOppdatering(newEntry, varsel))
    }

    private fun determineIfRenotifikasjon(currentStatus: EksternVarslingStatus, statusEvent: DoknotifikasjonStatusEvent): Boolean? {
        return when {
            determineInternalStatus(statusEvent) != Sendt -> null
            isFirstAttempt(currentStatus) -> false
            intervalSinceFirstAttempt(currentStatus, statusEvent) > Duration.ofHours(23) -> true
            else -> false
        }
    }

    private fun isFirstAttempt(currentStatus: EksternVarslingStatus): Boolean {
        return currentStatus.historikk.none { it.status == Sendt || it.status == Feilet }
    }

    private fun statusIsDuplicate(varsel: DatabaseVarsel, statusEvent: DoknotifikasjonStatusEvent): Boolean {

        return if (varsel.eksternVarslingStatus == null) {
            false
        } else {
            varsel.eksternVarslingStatus.historikk
                .filter { it.status == determineInternalStatus(statusEvent) }
                .filter { it.distribusjonsId == statusEvent.distribusjonsId }
                .filter { it.kanal == statusEvent.kanal }
                .filter { it.tidspunkt.truncatedTo(ChronoUnit.MILLIS) == statusEvent.tidspunkt.truncatedTo(ChronoUnit.MILLIS) }
                .any()
        }
    }

    private fun intervalSinceFirstAttempt(currentStatus: EksternVarslingStatus, statusEvent: DoknotifikasjonStatusEvent): Duration {
        val previous = currentStatus.historikk
            .filter { it.status == Sendt || it.status == Feilet }
            .minOf { it.tidspunkt }

        return Duration.between(previous, statusEvent.tidspunkt)
    }

    private fun buildOppdatering(newEntry: EksternVarslingHistorikkEntry, varsel: DatabaseVarsel) = EksternStatusOppdatering(
        status = newEntry.status,
        kanal = newEntry.kanal,
        varselType = varsel.type,
        varselId = varsel.varselId,
        ident = varsel.ident,
        namespace = varsel.produsent.namespace,
        appnavn = varsel.produsent.appnavn,
        renotifikasjon = newEntry.renotifikasjon
    )

    private fun determineInternalStatus(statusEvent: DoknotifikasjonStatusEvent): EksternStatus {
        return when(statusEvent.status) {
            FERDIGSTILT.name -> if (statusEvent.kanal.isNullOrBlank()) Ferdigstilt else Sendt
            INFO.name -> Info
            FEILET.name -> Feilet
            OVERSENDT.name -> Bestilt
            else -> throw IllegalArgumentException("Kjente ikke igjen doknotifikasjon status ${statusEvent.status}.")
        }
    }
}
