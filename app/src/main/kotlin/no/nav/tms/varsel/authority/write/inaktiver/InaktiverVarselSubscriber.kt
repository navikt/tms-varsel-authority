package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.observability.traceVarsel
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
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
        .withFields("varselId","produsent")
        .withOptionalFields("metadata")


    override suspend fun receive(jsonMessage: JsonMessage) {
        traceVarsel(id = jsonMessage["varselId"].asText(), mapOf("action" to "inaktiver")) {
            val inaktiverVarsel = objectMapper.treeToValue<InaktiverVarsel>(jsonMessage.json)
            log.info { "Inaktiver-event motatt" }

            val varsel = varselRepository.getVarsel(inaktiverVarsel.varselId)

            varsel?.let {
                MDC.put("initiated_by", varsel.produsent.namespace)

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
                    log.info { "Inaktiverte varsel etter event fra rapid" }
                } else {
                    log.info { "Behandlet inaktiver-event for allerede inaktivt varsel" }
                }
            } ?: log.info { "Fant ikke varsel" }
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
}
