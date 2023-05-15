package no.nav.tms.varsel.authority.write.aktiver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asOptionalZonedDateTime
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal class AktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselAktivertProducer: VarselAktivertProducer,
    private val metricsReporter: VarselMetricsReporter
) :
    River.PacketListener {

    private val log: Logger = LoggerFactory.getLogger(AktiverVarselSink::class.java)

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("beskjed", "oppgave", "innboks")) }
            validate { it.demandValue("aktiv", true) }
            validate { it.requireKey(
                "namespace",
                "appnavn",
                "eventId",
                "forstBehandlet",
                "fodselsnummer",
                "tekst",
                "link",
                "sikkerhetsnivaa",
                "eksternVarsling"
            ) }
            validate { it.interestedIn(
                "synligFremTil",
                "prefererteKanaler",
                "smsVarslingstekst",
                "epostVarslingstekst",
                "epostVarslingstittel"
            ) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        val dbVarsel = DatabaseVarsel(
            varsel = unpackVarsel(packet),
            produsent = unpackProdusent(packet),
            eksternVarslingBestilling = unpackEksternVarslingBestilling(packet),
            aktiv = true,
            opprettet = nowAtUtc(),
            aktivFremTil = packet["synligFremTil"].asOptionalZonedDateTime()
        )

        varselRepository.createVarsel(dbVarsel)
        varselAktivertProducer.varselAktivert(dbVarsel)
        metricsReporter.registerVarselAktivert(dbVarsel.type, dbVarsel.produsent)
        log.info("Behandlet ${dbVarsel.type}-varsel fra rapid med varselId ${dbVarsel.varselId}")
    }

    private fun unpackProdusent(packet: JsonMessage): Produsent {
        return Produsent(
            namespace = packet["namespace"].textValue(),
            appnavn = packet["appnavn"].textValue()
        )
    }

    private fun unpackVarsel(packet: JsonMessage): Varsel {
        return Varsel(
            type = packet["@event_name"].textValue().let { VarselType.parse(it) },
            varselId = packet["eventId"].textValue(),
            ident = packet["fodselsnummer"].textValue(),
            tekst = packet["tekst"].textValue(),
            link = packet["link"].textValue(),
            sikkerhetsnivaa = packet["sikkerhetsnivaa"].intValue(),
        )
    }

    private fun unpackEksternVarslingBestilling(packet: JsonMessage): EksternVarslingBestilling? {
        return if (packet["eksternVarsling"].booleanValue()) {
            EksternVarslingBestilling(
                prefererteKanaler = packet["prefererteKanaler"].map { it.textValue() },
                smsVarslingstekst = packet["smsVarslingstekst"].textValue(),
                epostVarslingstekst = packet["epostVarslingstekst"].textValue(),
                epostVarslingstittel = packet["epostVarslingstittel"].textValue()
            )
        } else {
            null
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
    }
}
