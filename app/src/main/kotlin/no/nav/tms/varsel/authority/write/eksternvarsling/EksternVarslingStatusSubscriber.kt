package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.observability.traceVarsel
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
import no.nav.tms.kafka.application.isMissingOrNull
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime

internal class EksternVarslingStatusSubscriber(
    private val eksternVarslingStatusUpdater: EksternVarslingStatusUpdater
) : Subscriber() {

    private val log = KotlinLogging.logger { }

    override fun subscribe(): Subscription = Subscription
        .forEvent("eksternVarslingStatus")
        .withFields(
            "eventId",
            "status",
            "melding",
            "bestillerAppnavn",
            "tidspunkt"
        )
        .withOptionalFields("distribusjonsId", "kanal")


    override suspend fun receive(jsonMessage: JsonMessage) {
        traceVarsel(id = jsonMessage["eventId"].asText(), mapOf("action" to "eksternVarsling")) {
            val eksternVarslingStatus = DoknotifikasjonStatusEvent(
                eventId = jsonMessage["eventId"].asText(),
                bestillerAppnavn = jsonMessage["bestillerAppnavn"].asText(),
                status = jsonMessage["status"].asText(),
                melding = jsonMessage["melding"].asText(),
                distribusjonsId = jsonMessage.getOrNull("distribusjonsId")?.asLongOrNull(),
                kanal = jsonMessage.getOrNull("kanal")?.asTextOrNull(),
                tidspunkt = jsonMessage["tidspunkt"].asZonedDateTime()
            )

            eksternVarslingStatusUpdater.updateEksternVarslingStatus(eksternVarslingStatus)
            log.info { "Behandlet eksternVarslingStatus" }
        }
    }

    private fun JsonNode.asLongOrNull() = if (isMissingOrNull()) null else asLong()


    private fun JsonNode.asTextOrNull() = if (isMissingOrNull()) null else asText()

}
