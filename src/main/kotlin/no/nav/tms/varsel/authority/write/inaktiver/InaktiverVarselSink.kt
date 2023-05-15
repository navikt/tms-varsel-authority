package no.nav.tms.varsel.authority.write.inaktiver

import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class InaktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer,
    private val metricsReporter: VarselMetricsReporter
) :
    River.PacketListener {

    private val log: Logger = LoggerFactory.getLogger(InaktiverVarselSink::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "done") }
            validate { it.requireKey("eventId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val varselId = getVarselId(packet)

        val varsel = varselRepository.getVarsel(varselId)

        if (varsel != null) {
            varselRepository.inaktiverVarsel(varselId, VarselInaktivertKilde.Produsent)

            metricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, VarselInaktivertKilde.Produsent)
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

        log.info("Inaktiverte varsel etter event fra rapid med varselId $varselId")
    }

    private fun getVarselId(packet: JsonMessage) = packet["eventId"].asText()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
    }
}
