package no.nav.tms.varsel.authority.eksternvarsling

import no.nav.tms.varsel.authority.sink.EksternStatus
import java.time.ZonedDateTime

data class DoknotifikasjonStatusEvent(
    val eventId: String,
    val bestillerAppnavn: String,
    val status: String,
    val melding: String,
    val distribusjonsId: Long?,
    val kanal: String?,
    val tidspunkt: ZonedDateTime
)

enum class DoknotifikasjonStatusEnum {
    FEILET, INFO, OVERSENDT, FERDIGSTILT;

    companion object {
        fun fromInternal(status: EksternStatus) = when(status) {
            EksternStatus.Feilet -> FEILET
            EksternStatus.Info -> INFO
            EksternStatus.Bestilt -> OVERSENDT
            EksternStatus.Sendt -> FERDIGSTILT
            EksternStatus.Ferdigstilt -> FERDIGSTILT
        }
    }
}
