package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotliquery.queryOf
import no.nav.tms.kafka.application.MessageBroadcaster
import no.nav.tms.varsel.authority.LocalDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.eksternvarsling.DoknotifikasjonStatusEnum.*
import no.nav.tms.varsel.authority.write.opprett.OpprettVarselSubscriber
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import no.nav.tms.varsel.authority.write.opprett.opprettVarselEvent
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.*


class EksternVarslingStatusSubscriberTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val varselRepository = WriteVarselRepository(database)


    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val eksternVarslingOppdatertProducer = EksternVarslingOppdatertProducer(mockProducer, "testtopic")
    private val eksternVarslingStatusRepository = EksternVarslingStatusRepository(database)
    private val eksternVarslingStatusUpdater =
        EksternVarslingStatusUpdater(
            eksternVarslingStatusRepository,
            varselRepository,
            eksternVarslingOppdatertProducer
        )
    private val testBroadcaster =
        MessageBroadcaster(
            listOf(
                EksternVarslingStatusSubscriber(eksternVarslingStatusUpdater = eksternVarslingStatusUpdater),
                OpprettVarselSubscriber(
                    varselRepository = varselRepository,
                    varselAktivertProducer = mockk(relaxed = true)
                )
            )
        )

    @BeforeEach
    fun resetDb() {
        database.update { queryOf("delete from varsel") }
        mockProducer.clear()
    }

    @Test
    fun `Lagrer ekstern varsling-status`() {

        val melding = "Sendt via epost"
        val distribusjonsId = 123L
        val kanal = "EPOST"

        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val doknotEvent = eksternVarslingStatus(
            varselId = varselId,
            status = FERDIGSTILT,
            melding = melding,
            distribusjonsId = distribusjonsId,
            kanal = kanal
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(doknotEvent)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe true

            it.historikk.size shouldBe 1
            it.historikk.first().melding shouldBe melding
            it.historikk.first().distribusjonsId shouldBe distribusjonsId
            it.kanaler shouldContain kanal
        }


        mockProducer.history().size shouldBe 1
    }

    @Test
    fun `Flere ekstern varsling-statuser oppdaterer basen`() {
        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val infoEvent = eksternVarslingStatus(varselId, status = INFO)
        val epostEvent = eksternVarslingStatus(varselId, status = FERDIGSTILT, kanal = "EPOST")
        val smsEvent = eksternVarslingStatus(varselId, status = FERDIGSTILT, kanal = "SMS")

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(infoEvent)
        testBroadcaster.broadcastJson(epostEvent)
        testBroadcaster.broadcastJson(smsEvent)

        val status = varselRepository.getVarsel(varselId)?.eksternVarslingStatus
        status.shouldNotBeNull()
        status.kanaler shouldContainExactlyInAnyOrder setOf("EPOST", "SMS")

        status.sendt shouldBe true

        mockProducer.history().size shouldBe 3
    }

    @Test
    fun `gjør ingenting hvis varselId er ukjent`() {

        val varselId = UUID.randomUUID().toString()

        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId))

        varselRepository.getVarsel(varselId)?.eksternVarslingStatus shouldBe null

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `varsler om oppdaterte varsler`() {
        val varselId = UUID.randomUUID().toString()

        testBroadcaster.broadcastJson(opprettVarselEvent("beskjed", varselId, ident = "12345678901"))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, OVERSENDT))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, INFO))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, FEILET))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, FERDIGSTILT, kanal = "SMS"))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, FERDIGSTILT, kanal = null))

        mockProducer.verifyOutput { output ->
            output.find { it["status"].textValue() == "bestilt" } shouldNotBe null
            output.find { it["status"].textValue() == "info" } shouldNotBe null
            output.find { it["status"].textValue() == "feilet" } shouldNotBe null
            output.find { it["status"].textValue() == "sendt" } shouldNotBe null
            output.find { it["status"].textValue() == "ferdigstilt" } shouldNotBe null

            val ferdigstilt = output.find { it["status"].textValue() == "sendt" }!!
            ferdigstilt["@event_name"].textValue() shouldBe "eksternStatusOppdatert"
            ferdigstilt["varselId"].textValue() shouldBe varselId
            ferdigstilt["ident"].textValue() shouldBe "12345678901"
            ferdigstilt["kanal"].textValue() shouldBe "SMS"
            ferdigstilt["renotifikasjon"].booleanValue() shouldBe false
            ferdigstilt["tidspunkt"].textValue().let { ZonedDateTime.parse(it) } shouldNotBe null
            ferdigstilt["produsent"]["cluster"].asText() shouldBe "cluster"
            ferdigstilt["produsent"]["namespace"].asText() shouldBe "namespace"
            ferdigstilt["produsent"]["appnavn"].asText() shouldBe "appnavn"
        }
    }

    @Test
    fun `sjekker om status kommer fra renotifikasjon`() {

        val varselId1 = UUID.randomUUID().toString()
        val varselId2 = UUID.randomUUID().toString()
        val varselId3 = UUID.randomUUID().toString()
        val varselId4 = UUID.randomUUID().toString()

        testBroadcaster.broadcastJson(opprettVarselEvent("oppgave", varselId1))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId1, OVERSENDT, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(
            eksternVarslingStatus(
                varselId1,
                FERDIGSTILT,
                kanal = "SMS",
                tidspunkt = nowAtUtc().plusDays(1)
            )
        )

        testBroadcaster.broadcastJson(opprettVarselEvent("oppgave", varselId2))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId2, OVERSENDT, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId2, FERDIGSTILT, kanal = "SMS", tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(
            eksternVarslingStatus(
                varselId2,
                FERDIGSTILT,
                kanal = "SMS",
                tidspunkt = nowAtUtc().plusDays(1)
            )
        )
        testBroadcaster.broadcastJson(
            eksternVarslingStatus(
                varselId2,
                FERDIGSTILT,
                kanal = "EPOST",
                tidspunkt = nowAtUtc().plusDays(1)
            )
        )

        testBroadcaster.broadcastJson(opprettVarselEvent("oppgave", varselId3))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId3, OVERSENDT, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId3, FEILET, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(
            eksternVarslingStatus(
                varselId3,
                FERDIGSTILT,
                kanal = "SMS",
                tidspunkt = nowAtUtc().plusDays(1)
            )
        )

        testBroadcaster.broadcastJson(opprettVarselEvent("oppgave", varselId4))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId4, OVERSENDT, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId4, FEILET, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId4, INFO, tidspunkt = nowAtUtc().plusDays(1)))

        val status1 = varselRepository.getVarsel(varselId1)?.eksternVarslingStatus
        status1!!.sendt shouldBe true
        status1.renotifikasjonSendt shouldBe false

        val status2 = varselRepository.getVarsel(varselId2)?.eksternVarslingStatus
        status2!!.sendt shouldBe true
        status2.renotifikasjonSendt shouldBe true

        val status3 = varselRepository.getVarsel(varselId3)?.eksternVarslingStatus
        status3!!.sendt shouldBe true
        status3.renotifikasjonSendt shouldBe true

        val status4 = varselRepository.getVarsel(varselId4)?.eksternVarslingStatus
        status4!!.sendt shouldBe false
        status4.renotifikasjonSendt shouldBe false


        mockProducer.verifyOutput { output ->
            output.filter {
                it["status"].textValue() == "sendt" && it["renotifikasjon"].asBoolean()
            }.size shouldBe 3

            output.filter {
                it["status"].textValue() == "sendt" && it["renotifikasjon"].asBoolean().not()
            }.size shouldBe 2

            output.filter {
                it["renotifikasjon"] == null
            }.size shouldBe 7
        }
    }

    @Test
    fun `sender med feilmelding for status feilet til kafka`() {


        val varselId = UUID.randomUUID().toString()

        testBroadcaster.broadcastJson(opprettVarselEvent("oppgave", varselId))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, OVERSENDT, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(eksternVarslingStatus(varselId, INFO, tidspunkt = nowAtUtc()))
        testBroadcaster.broadcastJson(
            eksternVarslingStatus(
                varselId,
                FERDIGSTILT,
                kanal = "SMS",
                tidspunkt = nowAtUtc().plusDays(1)
            )
        )
        testBroadcaster.broadcastJson(
            eksternVarslingStatus(
                varselId,
                FEILET,
                melding = "Ugyldig kontaktinfo",
                tidspunkt = nowAtUtc().plusDays(1)
            )
        )

        mockProducer.verifyOutput { output ->
            output.filter {
                it["status"].textValue() != "feilet"
            }.forEach {
                it["feilmelding"].shouldBeNull()
            }

            output.filter {
                it["status"].textValue() == "feilet"
            }.also {
                it.size shouldBe 1
            }.first().let {
                it["feilmelding"].textValue() shouldBe "Ugyldig kontaktinfo"
            }
        }
    }
}

private fun MockProducer<String, String>.verifyOutput(verifier: (List<JsonNode>) -> Unit) {
    val objectMapper = jacksonObjectMapper()
    history().map { it.value() }.map { objectMapper.readTree(it) }.let(verifier)
}
