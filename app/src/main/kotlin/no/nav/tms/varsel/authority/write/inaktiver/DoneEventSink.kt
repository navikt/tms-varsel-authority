package no.nav.tms.varsel.authority.write.inaktiver

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository

internal class DoneEventSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger {}

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "done") }
            validate { it.requireKey("eventId") }
        }.register(this)
    }

    private val staticMetadata = mapOf<String, Any>("inaktiver_event" to mapOf("source_topic" to "internal"))

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val varselId = getVarselId(packet)

        val varsel = varselRepository.getVarsel(varselId)

        if (varsel != null && varsel.aktiv) {
            varselRepository.inaktiverVarsel(varselId, VarselInaktivertKilde.Produsent, staticMetadata)

            VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, VarselInaktivertKilde.Produsent)
            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselType = varsel.type,
                    varselId = varsel.varselId,
                    namespace = varsel.produsent.namespace,
                    appnavn = varsel.produsent.appnavn,
                    kilde = VarselInaktivertKilde.Produsent
                )
            )
        }

        log.info { "Inaktiverte varsel etter event fra rapid med varselId $varselId" }
    }

    private fun getVarselId(packet: JsonMessage) = packet["eventId"].asText()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { problems.toString() }
    }
}