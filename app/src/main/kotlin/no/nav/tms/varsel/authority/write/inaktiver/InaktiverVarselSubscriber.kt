package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.observability.traceVarsel
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.MessageException
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
import no.nav.tms.kafka.application.isMissingOrNull
import no.nav.tms.varsel.action.InaktiverVarsel
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.slf4j.MDC

internal class InaktiverVarselSubscriber(
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) : Subscriber() {

    private val log = KotlinLogging.logger {}
    private val securelog = KotlinLogging.logger("secureLog")
    private val objectMapper = defaultObjectMapper()

    private val sourceTopic = "external"
    override fun subscribe(): Subscription = Subscription
        .forEvent("inaktiver")
        .withFields("varselId", "produsent")
        .withOptionalFields("metadata")


    override suspend fun receive(jsonMessage: JsonMessage) = traceInaktiverVarsel(jsonMessage) {
        log.info { "Inaktiver-event mottatt" }

        val inaktiverVarsel = deserialize(jsonMessage)
        val varsel = varselRepository.getVarsel(inaktiverVarsel.varselId)

        varsel?.let {

            if (varsel.aktiv) {
                varselRepository.inaktiverVarsel(
                    varselId = varsel.varselId,
                    kilde = VarselInaktivertKilde.Produsent,
                    metadata = mapMetadata(inaktiverVarsel)
                )

                VarselMetricsReporter.registerVarselInaktivert(
                    varseltype = varsel.type,
                    produsent = varsel.produsent,
                    kilde = VarselInaktivertKilde.Produsent,
                    sourceTopic = sourceTopic
                )
                varselInaktivertProducer.varselInaktivert(
                    VarselInaktivertHendelse(
                        varseltype = varsel.type,
                        varselId = varsel.varselId,
                        produsent = varsel.produsent,
                        kilde = VarselInaktivertKilde.Produsent
                    )
                )
                log.info { "Inaktiverte varsel etter event fra kafka" }
            } else {
                log.info { "Behandlet inaktiver-event for allerede inaktivt varsel" }
            }
        } ?: run {
            log.warn { "Fant ikke varsel å inaktivere" }
            throw InaktivertVarselMissingException()
        }
    }

    private fun deserialize(jsonMessage: JsonMessage): InaktiverVarsel {
        try {
            return objectMapper.treeToValue<InaktiverVarsel>(jsonMessage.json)
        } catch (e: JsonMappingException) {

            log.error { "Feil ved deserialisering av inaktiver-event" }
            securelog.error(e) { "Feil ved deserialisering av inaktiver-event [${jsonMessage.json}]" }

            throw InaktiverVarselDeserializationException()
        }
    }

    fun mapMetadata(inaktiverVarsel: InaktiverVarsel): Map<String, Any> {
        val inaktiverEvent = mutableMapOf(
            "source_topic" to sourceTopic,
            "produsent" to inaktiverVarsel.produsent
        )

        if (inaktiverVarsel.metadata != null) {
            inaktiverEvent += inaktiverVarsel.metadata!!
        }

        return mapOf("inaktiver_event" to inaktiverEvent)
    }

    private fun traceInaktiverVarsel(jsonMessage: JsonMessage, function: () -> Unit) {
        // Guard mot feilaktig format inne i produsent-objektet
        val produsent = jsonMessage["produsent"]["appnavn"]
            ?.takeIf { !it.isMissingOrNull() }
            ?.asText()
            ?: "ukjent"

        traceVarsel(
            id = jsonMessage["varselId"].asText(),
            extra = mapOf(
                "action" to "inaktiver",
                "initiated_by" to produsent
            ),
            function = function
        )
    }

    class InaktiverVarselDeserializationException: MessageException("Inaktiver-event har ikke riktig json-format")
    class InaktivertVarselMissingException : MessageException("Fant ikke inaktivert varsel")
}
