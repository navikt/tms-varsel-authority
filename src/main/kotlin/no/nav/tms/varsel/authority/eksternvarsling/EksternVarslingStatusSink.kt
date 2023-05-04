package no.nav.tms.varsel.authority.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class EksternVarslingStatusSink(
    rapidsConnection: RapidsConnection,
    private val eksternVarslingStatusUpdater: EksternVarslingStatusUpdater
) :
    River.PacketListener {

    private val log: Logger = LoggerFactory.getLogger(EksternVarslingStatusSink::class.java)

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
        val eksternVarslingStatus = DoknotifikasjonStatusEvent(
            eventId = packet["eventId"].asText(),
            bestillerAppnavn = packet["bestillerAppnavn"].asText(),
            status = packet["status"].asText(),
            melding = packet["melding"].asText(),
            distribusjonsId = packet["distribusjonsId"].asLongOrNull(),
            kanal = packet["kanal"].asTextOrNull(),
            tidspunkt = packet["tidspunkt"].asZonedDateTime()
        )

        runBlocking {
            eksternVarslingStatusUpdater.updateEksternVarslingStatus(eksternVarslingStatus)
            log.info("Behandlet eksternVarslingStatus fra rapid med eventid ${eksternVarslingStatus.eventId}")
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
    }

    private fun JsonNode.asLongOrNull() = if (isNull) null else asLong()

    private fun JsonNode.asTextOrNull() = if (isNull) null else asText()
}
