package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.action.InaktiverVarsel
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.config.rawJson
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import observability.traceVarsel
import org.slf4j.MDC

internal class InaktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger {}
    private val securelog = KotlinLogging.logger("secureLog")
    private val objectMapper = defaultObjectMapper()

    private val sourceTopic = "external"

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inaktiver") }
            validate {it.requireKey("varselId") }
            validate {it.interestedIn("metadata") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        traceVarsel(id = packet["varselId"].asText(), mapOf("action" to "inaktiver")) {
            val inaktiverVarsel = objectMapper.treeToValue<InaktiverVarsel>(packet.rawJson)
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
                            varselType = varsel.type,
                            varselId = varsel.varselId,
                            namespace = varsel.produsent.namespace,
                            appnavn = varsel.produsent.appnavn,
                            kilde = VarselInaktivertKilde.Produsent
                        )
                    )
                    log.info { "Inaktiverte varsel etter event fra rapid" }
                } else {
                    log.info { "Behandlet inaktiver-event for allerede inaktivt varsel" }
                }
            }?: log.info { "Fant ikke varsel" }
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

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { "Feil ved lesing av opprett-event fra kafka" }
        securelog.error { "Problem ved lesing av opprett-event fra kafka: $problems" }
    }
}
