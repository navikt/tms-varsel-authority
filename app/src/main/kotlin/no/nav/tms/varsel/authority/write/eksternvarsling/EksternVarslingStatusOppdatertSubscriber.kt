package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.logging.TeamLogs
import no.nav.tms.common.observability.traceVarsel
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.MessageException
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
import no.nav.tms.varsel.action.OpprettVarsel
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import java.time.ZonedDateTime

internal class EksternVarslingStatusOppdatertSubscriber(
    private val eksternVarslingStatusUpdater: EksternVarslingStatusUpdater
) : Subscriber() {

    private val log = KotlinLogging.logger { }
    private val teamLog = TeamLogs.logger { }

    private val objectMapper = defaultObjectMapper()

    override fun subscribe(): Subscription = Subscription
        .forEvent("eksternVarslingStatusOppdatert")
        .withAnyValue("status", "venter", "sendt", "feilet", "kansellert")
        .withFields(
            "varselId",
            "tidspunkt"
        )
        .withOptionalFields("renotifikasjon", "kanal", "feilmelding", "batch")


    override suspend fun receive(jsonMessage: JsonMessage) {
        traceVarsel(id = jsonMessage["varselId"].asText(), mapOf("action" to "eksternVarslingOppdatert")) {
            val oppdatertEvent = deserialize(jsonMessage)

            eksternVarslingStatusUpdater.updateEksternVarslingStatus(oppdatertEvent)
            log.info { "Behandlet eksternVarslingStatusOppdatert med status ${oppdatertEvent.status}" }
        }
    }

    private fun deserialize(jsonMessage: JsonMessage): EksternVarslingOppdatert {
        try {
            return objectMapper.treeToValue<EksternVarslingOppdatert>(jsonMessage.json)
        } catch (e: JsonMappingException) {

            log.error { "Feil ved deserialisering av eksternVarslingOppdatert-event" }
            teamLog.error(e) { "Feil ved deserialisering av eksternVarslingOppdatert-event [${jsonMessage.json}]" }

            throw StatusOppdatertDeserializationException()
        }
    }

    class StatusOppdatertDeserializationException: MessageException("eksternVarslingOppdatert-event har ikke riktig json-format")
}

data class EksternVarslingOppdatert(
    val varselId: String,
    val status: EksternStatus,
    val kanal: String?,
    val renotifikasjon: Boolean?,
    val batch: Boolean?,
    val feilmelding: String?,
    val tidspunkt: ZonedDateTime
)
