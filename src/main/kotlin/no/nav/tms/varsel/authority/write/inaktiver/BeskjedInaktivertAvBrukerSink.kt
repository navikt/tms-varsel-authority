package no.nav.tms.varsel.authority.write.inaktiver

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository

class BeskjedInaktivertAvBrukerSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger { }

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inaktivert") }
            validate { it.rejectValue("@source", "varsel-authority") }
            validate { it.demandValue("varselType", "beskjed") }
            validate { it.demandValue("kilde", "bruker") }
            validate { it.requireKey("eventId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val varselId = getVarselId(packet)

        val varsel = varselRepository.getVarsel(varselId)

        if (varsel != null && varsel.aktiv) {
            log.info { "Speiler bruker-initiert inaktivering av beskjed hos aggregator med varselId $varselId" }

            varselRepository.inaktiverVarsel(varselId, VarselInaktivertKilde.Bruker)

            VarselMetricsReporter.registerVarselInaktivert(varsel.type, varsel.produsent, VarselInaktivertKilde.Bruker)

            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselType = varsel.type,
                    varselId = varsel.varselId,
                    namespace = varsel.produsent.namespace,
                    appnavn = varsel.produsent.appnavn,
                    kilde = VarselInaktivertKilde.Bruker
                )
            )
        }
    }

    private fun getVarselId(packet: JsonMessage) = packet["eventId"].asText()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { problems.toString() }
    }
}
