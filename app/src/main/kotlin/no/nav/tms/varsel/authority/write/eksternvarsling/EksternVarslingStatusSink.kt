package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime
import no.nav.tms.common.observability.traceVarsel

internal class EksternVarslingStatusSink(
    rapidsConnection: RapidsConnection,
    private val eksternVarslingStatusUpdater: EksternVarslingStatusUpdater
) :
    River.PacketListener {

    private val log = KotlinLogging.logger { }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "eksternVarslingStatus") }
            validate {
                it.requireKey(
                    "eventId",
                    "status",
                    "melding",
                    "bestillerAppnavn",
                    "tidspunkt"
                )
            }
            validate { it.interestedIn("distribusjonsId", "kanal") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        traceVarsel(id = packet["eventId"].asText(), mapOf("action" to "eksternVarsling")) {
            val eksternVarslingStatus = DoknotifikasjonStatusEvent(
                eventId = packet["eventId"].asText(),
                bestillerAppnavn = packet["bestillerAppnavn"].asText(),
                status = packet["status"].asText(),
                melding = packet["melding"].asText(),
                distribusjonsId = packet["distribusjonsId"].asLongOrNull(),
                kanal = packet["kanal"].asTextOrNull(),
                tidspunkt = packet["tidspunkt"].asZonedDateTime()
            )

            eksternVarslingStatusUpdater.updateEksternVarslingStatus(eksternVarslingStatus)
            log.info { "Behandlet eksternVarslingStatus" }
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { problems.toString() }
    }

    private fun JsonNode.asLongOrNull() = if (isMissingOrNull()) null else asLong()

    private fun JsonNode.asTextOrNull() = if (isMissingOrNull()) null else asText()
}
