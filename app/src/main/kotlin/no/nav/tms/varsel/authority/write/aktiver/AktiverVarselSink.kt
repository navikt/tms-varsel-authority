package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.databind.JsonNode
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asOptionalZonedDateTime
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime
import no.nav.tms.varsel.authority.common.parseVarseltype
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.EksternVarslingBestilling
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.authority.common.traceOpprettVarsel
import org.postgresql.util.PSQLException

internal class AktiverVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselAktivertProducer: VarselAktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger { }

    private val sourceTopic = "internal"

    private val staticMetadata = mapOf<String, Any>("opprett_event" to mapOf("source_topic" to sourceTopic))

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("beskjed", "oppgave", "innboks")) }
            validate { it.demandValue("aktiv", true) }
            validate {
                it.requireKey(
                    "namespace",
                    "appnavn",
                    "eventId",
                    "forstBehandlet",
                    "fodselsnummer",
                    "tekst",
                    "link",
                    "sikkerhetsnivaa",
                    "eksternVarsling"
                )
            }
            validate {
                it.interestedIn(
                    "synligFremTil",
                    "prefererteKanaler",
                    "smsVarslingstekst",
                    "epostVarslingstittel",
                    "epostVarslingstekst"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val eventId = packet["eventId"].textValue()
        val produsent = unpackProdusent(packet)
        val eventName =  packet["@event_name"].asText()
        traceOpprettVarsel(
            id = eventId, initiatedBy = produsent.namespace,
            action = "aktiver",
            varseltype = packet["@event_name"].asText()
        ) {
            log.info { "Aktivert-event mottatt" }
            val dbVarsel = DatabaseVarsel(
                varselId = packet["eventId"].textValue(),
                type = packet["@event_name"].textValue().let(::parseVarseltype),
                ident = packet["fodselsnummer"].textValue(),
                sensitivitet = packet["sikkerhetsnivaa"].asSensitivitet(),
                innhold = unpackVarsel(packet),
                produsent = produsent,
                eksternVarslingBestilling = unpackEksternVarslingBestilling(packet),
                aktiv = true,
                opprettet = packet["forstBehandlet"].asZonedDateTime(),
                aktivFremTil = packet["synligFremTil"].asOptionalZonedDateTime(),
                metadata = staticMetadata
            )

            aktiverVarsel(dbVarsel)
        }
    }

    private fun aktiverVarsel(dbVarsel: DatabaseVarsel) {
        try {
            varselRepository.insertVarsel(dbVarsel)
            varselAktivertProducer.varselAktivert(dbVarsel)
            VarselMetricsReporter.registerVarselAktivert(dbVarsel.type, dbVarsel.produsent, sourceTopic)
            log.info { "Aktivert-event behandlet" }
        } catch (e: PSQLException) {
            log.warn(e) { "Feil ved aktivering av varsel" }
        }
    }

    private fun unpackProdusent(packet: JsonMessage) = DatabaseProdusent(
        cluster = null,
        namespace = packet["namespace"].textValue(),
        appnavn = packet["appnavn"].textValue()
    )


    private fun unpackVarsel(packet: JsonMessage): Innhold {
        return Innhold(
            tekst = packet["tekst"].textValue(),
            link = packet["link"].textValue().let { it.ifBlank { null } }
        )
    }

    private fun unpackEksternVarslingBestilling(packet: JsonMessage): EksternVarslingBestilling? {
        return if (packet["eksternVarsling"].booleanValue()) {
            EksternVarslingBestilling(
                prefererteKanaler = packet["prefererteKanaler"].map { it.textValue() }.map { EksternKanal.valueOf(it) },
                smsVarslingstekst = packet["smsVarslingstekst"].textValue(),
                epostVarslingstittel = packet["epostVarslingstittel"].textValue(),
                epostVarslingstekst = packet["epostVarslingstekst"].textValue()
            )
        } else {
            null
        }
    }

    private fun JsonNode.asSensitivitet(): Sensitivitet {
        return when (intValue()) {
            3 -> Sensitivitet.Substantial
            4 -> Sensitivitet.High
            else -> throw IllegalArgumentException("Feil verdi for 'sikkerhetsnivaa': ${textValue()}")
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { problems.toString() }
    }
}
