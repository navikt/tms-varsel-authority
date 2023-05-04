package no.nav.tms.varsel.authority.sink

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.tms.varsel.authority.done.VarselInaktivertHendelse
import no.nav.tms.varsel.authority.done.VarselInaktivertKilde.Produsent
import no.nav.tms.varsel.authority.done.VarselInaktivertProducer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class InaktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: VarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
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
            varselRepository.inaktiverVarsel(varselId, Produsent)

            varselInaktivertProducer.varselInaktivert(
                VarselInaktivertHendelse(
                    varselType = varsel.type,
                    varselId = varsel.varselId,
                    namespace = varsel.produsent.namespace,
                    appnavn = varsel.produsent.appnavn,
                    kilde = Produsent
                )
            )
        }

        log.info("Behandlet inaktiverte varsel etter event fra rapid med varselId $varselId")
    }

    private fun getVarselId(packet: JsonMessage) = packet["eventId"].asText()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
    }
}
