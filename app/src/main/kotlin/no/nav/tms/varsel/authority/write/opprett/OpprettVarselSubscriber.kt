package no.nav.tms.varsel.authority.write.opprett

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.tms.common.logging.TeamLogs
import no.nav.tms.common.observability.traceVarsel
import no.nav.tms.kafka.application.JsonMessage
import no.nav.tms.kafka.application.MessageException
import no.nav.tms.kafka.application.Subscriber
import no.nav.tms.kafka.application.Subscription
import no.nav.tms.kafka.application.isMissingOrNull
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.common.UniqueConstraintException
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
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

    private val teamLog = TeamLogs.logger { }
    private val objectMapper = defaultObjectMapper()
    private val sourceTopic = "external"


    override suspend fun receive(jsonMessage: JsonMessage) = traceOpprettVarsel(jsonMessage) {
        log.info { "Opprett-event motatt" }

        deserialize(jsonMessage)
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
                    eksternVarslingBestilling = applyEksternVarslingDefaults(it),
                    opprettet = nowAtUtc(),
                    aktivFremTil = it.aktivFremTil,
                    metadata = mapMetadata(it)
                )
            }.let {
                opprettVarsel(it)
            }
    }

    private fun opprettVarsel(dbVarsel: DatabaseVarsel) {
        try {
            varselRepository.insertVarsel(dbVarsel)
            varselAktivertProducer.varselOpprettet(dbVarsel)
            VarselMetricsReporter.registerVarselAktivert(dbVarsel.type, dbVarsel.produsent, sourceTopic)
            log.info { "Opprett varsel fra kafka behandlet" }

        } catch (e: UniqueConstraintException) {
            log.info { "Ignorerte duplikat varsel" }
            throw DuplikatVarselException()
        } catch (e: PSQLException) {
            log.error(e) { "Feil ved oppretting av varsel" }
            throw MessageException("Uventet feil ved oppretting av varsel")
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

        if (opprettVarsel.eksternVarsling != null) {
            opprettEvent += "bruk_default_kan_batches" to (opprettVarsel.eksternVarsling?.kanBatches == null)
        }

        if (opprettVarsel.metadata != null) {
            opprettEvent += opprettVarsel.metadata!!
        }

        return mapOf("opprett_event" to opprettEvent)
    }

    private fun deserialize(jsonMessage: JsonMessage): OpprettVarsel {
        try {
            return objectMapper.treeToValue<OpprettVarsel>(jsonMessage.json)
        } catch (e: JsonMappingException) {

            log.error { "Feil ved deserialisering av opprett-event" }
            teamLog.error(e) { "Feil ved deserialisering av opprett-event [${jsonMessage.json}]" }

            throw OpprettVarselDeserializationException()
        }
    }

    private fun validate(opprettVarsel: OpprettVarsel) {
        try {
            OpprettVarselValidation.validate(opprettVarsel)
        } catch (e: VarselValidationException) {
            log.warn { "Feil ved validering av opprett-varsel event med id [${opprettVarsel.varselId}]" }
            teamLog.warn { "Feil ved validering av opprett-varsel event med id [${opprettVarsel.varselId}]: ${e.explanation.joinToString()}" }

            throw OpprettVarselValidationException()
        }
    }

    private fun applyEksternVarslingDefaults(opprettVarsel: OpprettVarsel) : EksternVarslingBestilling? {
        return if (opprettVarsel.eksternVarsling != null && opprettVarsel.eksternVarsling?.kanBatches == null) {
            val default = when (opprettVarsel.type) {
                Varseltype.Oppgave, Varseltype.Innboks -> false
                else -> !eksterneTeksterErSpesifisert(opprettVarsel.eksternVarsling!!)
            }

            opprettVarsel.eksternVarsling!!.copy(kanBatches = default)
        } else {
            opprettVarsel.eksternVarsling
        }
    }

    private fun eksterneTeksterErSpesifisert(eksternVarsling: EksternVarslingBestilling): Boolean {
        return eksternVarsling.smsVarslingstekst != null || eksternVarsling.epostVarslingstekst != null
    }

    private fun traceOpprettVarsel(jsonMessage: JsonMessage, function: () -> Unit) {
        // Guard mot feilaktig format inne i produsent-objektet
        val produsent = jsonMessage["produsent"]["appnavn"]
            ?.takeIf { !it.isMissingOrNull() }
            ?.asText()
            ?: "ukjent"

        traceVarsel(
            id = jsonMessage["varselId"].asText(),
            extra = mapOf(
                "action" to "opprett",
                "initiated_by" to produsent,
                "type" to jsonMessage["type"].asText()
            ),
            function = function
        )
    }

    class DuplikatVarselException: MessageException("Varsel med samme varselId finnes allerede")
    class OpprettVarselDeserializationException: MessageException("Opprett-event har ikke riktig json-format")
    class OpprettVarselValidationException: MessageException("Varsel består ikke validering")
}
