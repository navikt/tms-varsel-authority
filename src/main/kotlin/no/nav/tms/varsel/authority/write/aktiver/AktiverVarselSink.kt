package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.databind.JsonNode
import mu.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asOptionalZonedDateTime
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import org.postgresql.util.PSQLException

internal class AktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselAktivertProducer: VarselAktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger { }

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
            varselId = packet["eventId"].textValue(),
            type = packet["@event_name"].textValue().let { VarselType.parse(it) },
            ident = packet["fodselsnummer"].textValue(),
            sensitivitet = packet["sikkerhetsnivaa"].asSensitivitet(),
            innhold = unpackVarsel(packet),
            produsent = unpackProdusent(packet),
            eksternVarslingBestilling = unpackEksternVarslingBestilling(packet),
            aktiv = true,
            opprettet = packet["forstBehandlet"].asZonedDateTime(),
            aktivFremTil = packet["synligFremTil"].asOptionalZonedDateTime()
        )

        aktiverVarsel(dbVarsel)
    }

    private fun aktiverVarsel(dbVarsel: DatabaseVarsel) {
        try {
            varselRepository.insertVarsel(dbVarsel)
            varselAktivertProducer.varselAktivert(dbVarsel)
            VarselMetricsReporter.registerVarselAktivert(dbVarsel.type, dbVarsel.produsent)
            log.info("Behandlet ${dbVarsel.type}-varsel fra rapid med varselId ${dbVarsel.varselId}")
        } catch (e: PSQLException) {
            log.warn("Feil ved aktivering av varsel med id [${dbVarsel.varselId}].", e)
        }
    }

    private fun unpackProdusent(packet: JsonMessage): Produsent {
        return Produsent(
            namespace = packet["namespace"].textValue(),
            appnavn = packet["appnavn"].textValue()
        )
    }

    private fun unpackVarsel(packet: JsonMessage): Innhold {
        return Innhold(
            tekst = packet["tekst"].textValue(),
            link = packet["link"].textValue().let { it.ifBlank { null } }
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

    private fun JsonNode.asSensitivitet(): Sensitivitet {
        return when(intValue()) {
            3 -> Sensitivitet.Substantial
            4 -> Sensitivitet.High
            else -> throw IllegalArgumentException("Feil verdi for 'sikkerhetsnivaa': ${textValue()}")
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
    }
}
