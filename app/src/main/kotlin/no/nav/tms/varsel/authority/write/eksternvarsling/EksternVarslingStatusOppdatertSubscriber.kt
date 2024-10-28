package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.observability.traceVarsel
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import java.time.ZonedDateTime

internal class EksternVarslingStatusOppdatertSubscriber(
    private val eksternVarslingStatusUpdater: EksternVarslingStatusUpdater
) : Subscriber() {

    private val log = KotlinLogging.logger { }

    private val objectMapper = defaultObjectMapper()

    override fun subscribe(): Subscription = Subscription
        .forEvent("eksternVarslingStatusOppdatert")
        .withAnyValue("status", "sendt", "feilet")
        .withFields(
            "varselId",
            "tidspunkt"
        )
        .withOptionalFields("renotifikasjon", "kanal", "feilmelding", "batch")


    override suspend fun receive(jsonMessage: JsonMessage) {
        traceVarsel(id = jsonMessage["varselId"].asText(), mapOf("action" to "eksternVarslingOppdatert")) {
            val oppdatertEvent: EksternVarslingOppdatert = objectMapper.treeToValue(jsonMessage.json)

            eksternVarslingStatusUpdater.updateEksternVarslingStatus(oppdatertEvent)
            log.info { "Behandlet eksternVarslingStatusOppdatert med status ${oppdatertEvent.status}" }
        }
    }
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
