package no.nav.tms.varsel.authority.write.inaktiver

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import observability.traceVarsel
import org.slf4j.MDC

internal class DoneEventSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger {}
    private val securelog = KotlinLogging.logger("secureLog")

    private val sourceTopic = "internal"

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "done") }
            validate { it.requireKey("eventId") }
        }.register(this)
    }

    private val staticMetadata = mapOf<String, Any>("inaktiver_event" to mapOf("source_topic" to sourceTopic))

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        val varselId = getVarselId(packet)

        traceVarsel(id = varselId, mapOf("action" to "done")) {
            log.info { "Done-event motatt" }
            val varsel = varselRepository.getVarsel(varselId)

            varsel?.let {
                MDC.put("initiated_by", varsel.produsent.namespace)

                if (varsel.aktiv) {

                    varselRepository.inaktiverVarsel(varselId, VarselInaktivertKilde.Produsent, staticMetadata)

                    VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, VarselInaktivertKilde.Produsent, sourceTopic)
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
                    log.info { "Behandlet done-event for allerede inaktivt varsel" }
                }
            }?: log.info { "Fant ikke varsel" }
        }
    }

    private fun getVarselId(packet: JsonMessage) = packet["eventId"].asText()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { "Feil ved lesing av done-event" }
        securelog.error { "Feil ved lesing av done-event: ${problems.toExtendedReport()}" }
    }
}
