package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.module.kotlin.treeToValue
import io.github.oshai.kotlinlogging.KotlinLogging
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
import org.postgresql.util.PSQLException

internal class OpprettVarselSink(
    rapidsConnection: RapidsConnection,
    private val varselRepository: WriteVarselRepository,
    private val varselAktivertProducer: VarselAktivertProducer
) :
    River.PacketListener {

    private val log = KotlinLogging.logger { }
    private val objectMapper = defaultObjectMapper()

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "opprett") }
            validate { it.requireKey(
                "type",
                "varselId",
                "ident",
                "sensitivitet",
                "innhold",
                "metadata"
            ) }
            validate { it.interestedIn(
                "eksternVarsling",
                "aktivFremTil",
            ) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        objectMapper.treeToValue<OpprettVarsel>(packet.rawJson)
            .also { OpprettVarselValidation.validate(it) }
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
                    metadata = it.metadata
                )
            }.let {
                aktiverVarsel(it)
            }
    }

    private fun aktiverVarsel(dbVarsel: DatabaseVarsel) {
        try {
            varselRepository.insertVarsel(dbVarsel)
            varselAktivertProducer.varselAktivert(dbVarsel)
            VarselMetricsReporter.registerVarselAktivert(dbVarsel.type, dbVarsel.produsent)
            log.info { "Behandlet ${dbVarsel.type}-varsel fra rapid med varselId ${dbVarsel.varselId}" }
        } catch (e: PSQLException) {
            log.warn(e) { "Feil ved aktivering av varsel med id [${dbVarsel.varselId}]." }
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


    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error { problems.toString() }
    }
}
