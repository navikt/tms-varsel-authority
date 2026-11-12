package no.nav.tms.varsel.authority.write.eksternvarsling

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.oshai.kotlinlogging.withLoggingContext
import no.nav.tms.kafka.application.SkippableMessageException
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
            val updatedStatus = when (statusEvent.status) {
                Sendt -> applySendtEvent(currentStatus, statusEvent)
                Feilet -> applyFeiletEvent(currentStatus, statusEvent)
                else -> {
                    currentStatus.copy(
                        sisteStatus = statusEvent.status,
                        sistOppdatert = nowAtUtc()
                    )
                }
            }
            eksternVarslingStatusRepository.updateEksternVarslingStatus(varsel.varselId, updatedStatus)
        }
    }

    private fun applySendtEvent(currentStatus: EksternVarslingStatus, statusEvent: EksternVarslingOppdatert): EksternVarslingStatus {
        return if (statusEvent.renotifikasjon == true) {
            currentStatus.copy(
                sendt = true,
                renotifikasjonSendt = true,
                renotifikasjonTidspunkt = statusEvent.tidspunkt,
                renotifikasjoner = when (val renotifikasjon = mapSending(statusEvent)) {
                    null -> currentStatus.renotifikasjoner
                    else -> currentStatus.renotifikasjoner + renotifikasjon
                },
                kanaler = (currentStatus.kanaler + statusEvent.kanal).filterNotNull().distinct(),
                sisteStatus = Sendt,
                sistOppdatert = nowAtUtc()
            )
        } else {
            currentStatus.copy(
                sendt = true,
                sendtSomBatch = currentStatus.sendtSomBatch || statusEvent.batch == true,
                sending = mapSending(statusEvent),
                sendtTidspunkt = statusEvent.tidspunkt,
                kanaler = (currentStatus.kanaler + statusEvent.kanal).filterNotNull().distinct(),
                sisteStatus = Sendt,
                sistOppdatert = nowAtUtc()
            )
        }
    }

    private fun mapSending(statusEvent: EksternVarslingOppdatert): EksternVarslingSending? {
        if (statusEvent.sendtTekst == null) {
            return null
        }

        return when (statusEvent.kanal?.lowercase()) {
            "epost" -> EksternVarslingSending(
                kanal = statusEvent.kanal,
                tittel = statusEvent.sendtTekst.epostTittel!!,
                tekst = statusEvent.sendtTekst.epostTekst!!,
                tidspunkt = statusEvent.tidspunkt
            )
            "sms" -> EksternVarslingSending(
                kanal = statusEvent.kanal,
                tittel = null,
                tekst = statusEvent.sendtTekst.smsTekst!!,
                tidspunkt = statusEvent.tidspunkt
            )
            else -> null
        }
    }

    private fun applyFeiletEvent(currentStatus: EksternVarslingStatus, statusEvent: EksternVarslingOppdatert): EksternVarslingStatus {
        return currentStatus.copy(
            feilhistorikk = if (statusEvent.feilmelding == null) {
                currentStatus.feilhistorikk
            } else {
                EksternFeilHistorikkEntry(
                    feilmelding = statusEvent.feilmelding,
                    tidspunkt = statusEvent.tidspunkt,
                ).let {
                    currentStatus.feilhistorikk + it
                }.distinct()
            },
            sisteStatus = Feilet,
            sistOppdatert = nowAtUtc()
        )
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

class UpdatedVarselMissingException : SkippableMessageException("Fant ikke varsel tilhørende ekstern varseloppdatering")
class FeilhistorikkFullException: SkippableMessageException("Ignorerer feil-status; Feilhistorikken er full")
