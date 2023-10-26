package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.*
import no.nav.tms.varsel.action.InaktiverVarsel
import no.nav.tms.varsel.action.OpprettVarsel
import no.nav.tms.varsel.action.OpprettVarselValidation
import no.nav.tms.varsel.action.VarselValidationException
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.config.rawJson
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository

internal class InaktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselInaktivertProducer: VarselInaktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger {}
    private val securelog = KotlinLogging.logger("secureLog")
    private val objectMapper = defaultObjectMapper()

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inaktiver") }
            validate {it.requireKey("varselId") }
            validate {it.interestedIn("metadata") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val inaktiverVarsel = objectMapper.treeToValue<InaktiverVarsel>(packet.rawJson)

        val varsel = varselRepository.getVarsel(inaktiverVarsel.varselId)

        if (varsel != null && varsel.aktiv) {
            varselRepository.inaktiverVarsel(
                varselId = varsel.varselId,
                kilde = VarselInaktivertKilde.Produsent,
                metadata = mapMetadata(inaktiverVarsel)
            )

            VarselMetricsReporter.registerVarselInaktivert(
                varsel.type,
                varsel.produsent,
                VarselInaktivertKilde.Produsent
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
        }
    }

    fun mapMetadata(inaktiverVarsel: InaktiverVarsel): Map<String, Any> {
        val inaktiverEvent = mutableMapOf(
            "source_topic" to "external",
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
