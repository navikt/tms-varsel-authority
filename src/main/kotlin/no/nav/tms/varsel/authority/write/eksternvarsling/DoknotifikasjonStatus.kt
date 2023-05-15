package no.nav.tms.varsel.authority.write.eksternvarsling

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
    FEILET, INFO, OVERSENDT, FERDIGSTILT
}
