package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tms.varsel.authority.LocalDateTimeHelper.nowAtUtc
import java.time.LocalDateTime

fun eksternVarslingStatus(
    varselId: String,
    status: DoknotifikasjonStatusEnum = DoknotifikasjonStatusEnum.INFO,
    bestillerAppnavn: String  = "appnavn",
    melding: String = "Melding",
    distribusjonsId: Long? = null,
    kanal: String? = null,
    tidspunkt: LocalDateTime = nowAtUtc()
) = EksternVarslingStatusEvent(
    eventId = varselId,
    bestillerAppnavn = bestillerAppnavn,
    status = status.name,
    melding = melding,
    distribusjonsId = distribusjonsId,
    kanal = kanal,
    tidspunkt = tidspunkt,
)

data class EksternVarslingStatusEvent(
    val eventId: String,
    val bestillerAppnavn: String,
    val status: String,
    val melding: String,
    val distribusjonsId: Long?,
    val kanal: String?,
    val tidspunkt: LocalDateTime
) {
    @JsonProperty("@event_name") val eventName = "eksternVarslingStatus"
}
