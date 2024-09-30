package no.nav.tms.varsel.authority.write.opprett

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.MessageException
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.common.traceOpprettVarsel
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import org.postgresql.util.PSQLException

internal class OpprettVarselSubscriber(
    private val varselRepository: WriteVarselRepository,
    private val varselAktivertProducer: VarselOpprettetProducer
) : Subscriber() {

    private val log = KotlinLogging.logger { }

    override fun subscribe(): Subscription = Subscription
        .forEvent("opprett")
        .withFields(
            "type",
            "varselId",
            "ident",
            "sensitivitet",
            "tekster",
            "produsent"
        )
        .withOptionalFields(
            "eksternVarsling",
            "aktivFremTil",
            "link",
            "metadata"
        )

    private val securelog = KotlinLogging.logger("secureLog")
    private val objectMapper = defaultObjectMapper()
    private val sourceTopic = "external"


    override suspend fun receive(jsonMessage: JsonMessage) {
        traceOpprettVarsel(
            id = jsonMessage["varselId"].asText(),
            initiatedBy = jsonMessage["produsent"]["namespace"].asText(),
            action = "opprett", varseltype = jsonMessage["type"].asText()
        ) {
            log.info { "Opprett-event motatt" }
            objectMapper.treeToValue<OpprettVarsel>(jsonMessage.json)
                .also { validate(it) }
                .let { applyEksternVarslingDefaults(it) }
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
            log.info { "Opprett varsel fra kafka behandlet}" }
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

    private fun validate(opprettVarsel: OpprettVarsel) {
        try {
            OpprettVarselValidation.validate(opprettVarsel)
        } catch (e: VarselValidationException) {
            log.warn { "Feil ved validering av opprett-varsel event med id [${opprettVarsel.varselId}]" }
            securelog.warn { "Feil ved validering av opprett-varsel event med id [${opprettVarsel.varselId}]: ${e.explanation.joinToString()}" }

            throw MessageException("OpprettVarsel event did not pass validation")
        }
    }

    private fun applyEksternVarslingDefaults(opprettVarsel: OpprettVarsel) : OpprettVarsel {
        return if (opprettVarsel.eksternVarsling != null && opprettVarsel.eksternVarsling?.kanBatches == null) {
            val default = when (opprettVarsel.type) {
                Varseltype.Oppgave, Varseltype.Innboks -> false
                else -> !eksterneTeksterErSpesifisert(opprettVarsel.eksternVarsling!!)
            }
            val bestilling = opprettVarsel.eksternVarsling!!.copy(kanBatches = default)
            opprettVarsel.copy(eksternVarsling = bestilling)
        } else {
            opprettVarsel
        }
    }

    private fun eksterneTeksterErSpesifisert(eksternVarsling: EksternVarslingBestilling): Boolean {
        return eksternVarsling.smsVarslingstekst != null || eksternVarsling.epostVarslingstekst != null
    }
}
