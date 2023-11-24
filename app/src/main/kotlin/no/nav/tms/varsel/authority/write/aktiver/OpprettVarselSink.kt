package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.config.validate.ValidationException
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.config.rawJson
import no.nav.tms.varsel.action.OpprettVarsel
import no.nav.tms.varsel.action.OpprettVarselValidation
import no.nav.tms.varsel.action.VarselValidationException
import no.nav.tms.varsel.authority.common.traceVarselSink
import org.postgresql.util.PSQLException

internal class OpprettVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselAktivertProducer: VarselAktivertProducer
) : River.PacketListener {

    private val log = KotlinLogging.logger { }
    private val securelog = KotlinLogging.logger("secureLog")
    private val objectMapper = defaultObjectMapper()

    private val sourceTopic = "external"

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "opprett") }
            validate {
                it.requireKey(
                    "type",
                    "varselId",
                    "ident",
                    "sensitivitet",
                    "tekster",
                    "produsent"
                )
            }
            validate {
                it.interestedIn(
                    "eksternVarsling",
                    "aktivFremTil",
                    "link",
                    "metadata"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        traceVarselSink(
            id = packet["varselId"].asText(),
            initiatedBy = packet["produsent"]["namespace"].asText(),
            action = "opprett", varseltype = packet["type"].asText()
        ) {

            objectMapper.treeToValue<OpprettVarsel>(packet.rawJson)
                .also { validate(it) }
                .let {
                    DatabaseVarsel(
                        aktiv = true,
                        type = it.type,
                        varselId = it.varselId,
                        ident = it.ident,
                        sensitivitet = it.sensitivitet,
                        innhold = mapInnhold(it),
                        produsent = mapProdusent(it),
                        eksternVarslingBestilling = it.eksternVarsling,
                        opprettet = nowAtUtc(),
                        aktivFremTil = it.aktivFremTil,
                        metadata = mapMetadata(it)
                    )
                }.let {
                    aktiverVarsel(it)
                }
        }
    }

    private fun aktiverVarsel(dbVarsel: DatabaseVarsel) {
        try {
            varselRepository.insertVarsel(dbVarsel)
            varselAktivertProducer.varselAktivert(dbVarsel)
            VarselMetricsReporter.registerVarselAktivert(dbVarsel.type, dbVarsel.produsent, sourceTopic)
            log.info { "Behandlet varsel fra rapid}" }
        } catch (e: PSQLException) {
            log.warn(e) { "Feil ved aktivering av varsel" }
        }
    }

    private fun mapInnhold(opprettVarsel: OpprettVarsel): Innhold {
        val defaultTekst = with(opprettVarsel.tekster) {
            if (size == 1) {
                first().tekst
            } else {
                first { it.default }.tekst
            }
        }

        return Innhold(
            link = opprettVarsel.link,
            tekst = defaultTekst,
            tekster = opprettVarsel.tekster
        )
    }

    private fun mapProdusent(opprettVarsel: OpprettVarsel) =
        DatabaseProdusent(
            cluster = opprettVarsel.produsent.cluster,
            namespace = opprettVarsel.produsent.namespace,
            appnavn = opprettVarsel.produsent.appnavn,
        )

    private fun mapMetadata(opprettVarsel: OpprettVarsel): Map<String, Any> {
        val opprettEvent = mutableMapOf<String, Any>(
            "source_topic" to sourceTopic
        )

        if (opprettVarsel.metadata != null) {
            opprettEvent += opprettVarsel.metadata!!
        }

        return mapOf("opprett_event" to opprettEvent)
    }

    private fun validate(opprettVarsel: OpprettVarsel) = try {
        OpprettVarselValidation.validate(opprettVarsel)
    } catch (e: VarselValidationException) {
        log.warn { "Feil ved validering av opprett-varsel event med id [${opprettVarsel.varselId}]" }
        securelog.warn { "Feil ved validering av opprett-varsel event med id [${opprettVarsel.varselId}]: ${e.explanation.joinToString()}" }

        throw MessageProblems.MessageException(MessageProblems("OpprettVarsel event did not pass validation"))
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { "Feil ved lesing av opprett-event fra kafka" }
        securelog.error { "Problem ved lesing av opprett-event fra kafka: $problems" }
    }
}
