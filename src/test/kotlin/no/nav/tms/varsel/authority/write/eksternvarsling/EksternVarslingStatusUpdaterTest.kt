package no.nav.tms.varsel.authority.write.eksternvarsling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.authority.LocalDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.eksternvarsling.DoknotifikasjonStatusEnum.*
import no.nav.tms.varsel.authority.write.aktiver.AktiverVarselSink
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import no.nav.tms.varsel.authority.write.aktiver.aktiverVarselEvent
import no.nav.tms.varsel.authority.toJson
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime


class EksternVarslingStatusSinkTest {

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
        EksternVarslingStatusUpdater(eksternVarslingStatusRepository, varselRepository, eksternVarslingOppdatertProducer)

    @BeforeEach
    fun resetDb() {
        database.update { queryOf("delete from varsel") }
        mockProducer.clear()
    }

    @Test
    fun `Lagrer ekstern varsling-status`() {
        val testRapid = TestRapid()
        setupEksternVarslingStatusSink(testRapid)
        setupVarselSink(testRapid)

        val varselEvent = aktiverVarselEvent("beskjed", "111")
        val doknotEvent = eksternVarslingStatus(
            varselId = "111",
            status = FERDIGSTILT,
            kanal = "EPOST",
            distribusjonsId = 123L,
            melding = "Sendt via epost"
        )

        testRapid.sendMessageAsJson(varselEvent)
        testRapid.sendMessageAsJson(doknotEvent)

        val dbVarsel = varselRepository.getVarsel("111")

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe true

            it.historikk.size shouldBe 1
            it.historikk.first().melding shouldBe doknotEvent.melding
            it.historikk.first().distribusjonsId shouldBe doknotEvent.distribusjonsId
            it.kanaler shouldContain doknotEvent.kanal
        }


        mockProducer.history().size shouldBe 1
    }

    @Test
    fun `Flere ekstern varsling-statuser oppdaterer basen`() {
        val testRapid = TestRapid()
        setupEksternVarslingStatusSink(testRapid)
        setupVarselSink(testRapid)

        val varselEvent = aktiverVarselEvent("beskjed", "111")
        val infoEvent = eksternVarslingStatus("111", status = INFO)
        val epostEvent = eksternVarslingStatus("111", status = FERDIGSTILT, kanal = "EPOST")
        val smsEvent = eksternVarslingStatus("111", status = FERDIGSTILT, kanal = "SMS")

        testRapid.sendMessageAsJson(varselEvent)
        testRapid.sendMessageAsJson(infoEvent)
        testRapid.sendMessageAsJson(epostEvent)
        testRapid.sendMessageAsJson(smsEvent)

        val status = varselRepository.getVarsel("111")?.eksternVarslingStatus
        status shouldNotBe null
        status!!.kanaler shouldContainExactlyInAnyOrder setOf("EPOST", "SMS")

        status.sendt shouldBe true

        mockProducer.history().size shouldBe 3
    }

    @Test
    fun `gjør ingenting hvis varselId er ukjent`() {
        val testRapid = TestRapid()
        setupEksternVarslingStatusSink(testRapid)

        testRapid.sendMessageAsJson(eksternVarslingStatus("111"))

        varselRepository.getVarsel("111")?.eksternVarslingStatus shouldBe null

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `varsler om oppdaterte varsler`() {
        val testRapid = TestRapid()
        setupEksternVarslingStatusSink(testRapid)
        setupVarselSink(testRapid)

        testRapid.sendMessageAsJson(aktiverVarselEvent("beskjed", "111", fodselsnummer = "147"))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", OVERSENDT))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", INFO))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", FEILET))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", FERDIGSTILT, kanal = "SMS"))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", FERDIGSTILT, kanal = null))

        mockProducer.verifyOutput { output ->
            output.find { it["status"].textValue() == "bestilt" } shouldNotBe null
            output.find { it["status"].textValue() == "info" } shouldNotBe null
            output.find { it["status"].textValue() == "feilet" } shouldNotBe null
            output.find { it["status"].textValue() == "sendt" } shouldNotBe null
            output.find { it["status"].textValue() == "ferdigstilt" } shouldNotBe null

            val ferdigstilt = output.find { it["status"].textValue() == "sendt" }!!
            ferdigstilt["@event_name"].textValue() shouldBe "eksternStatusOppdatert"
            ferdigstilt["eventId"].textValue() shouldBe "111"
            ferdigstilt["ident"].textValue() shouldBe "147"
            ferdigstilt["kanal"].textValue() shouldBe "SMS"
            ferdigstilt["renotifikasjon"].booleanValue() shouldBe false
            ferdigstilt["tidspunkt"].textValue().let { ZonedDateTime.parse(it) } shouldNotBe null
        }
    }

    @Test
    fun `sjekker om status kommer fra renotifikasjon`() {
        val testRapid = TestRapid()
        setupEksternVarslingStatusSink(testRapid)
        setupVarselSink(testRapid)

        testRapid.sendMessageAsJson(aktiverVarselEvent("oppgave", "111"))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", OVERSENDT, tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("111", FERDIGSTILT, kanal = "SMS", tidspunkt = nowAtUtc().plusDays(1)))

        testRapid.sendMessageAsJson(aktiverVarselEvent("oppgave", "222"))
        testRapid.sendMessageAsJson(eksternVarslingStatus("222", OVERSENDT, tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("222", FERDIGSTILT, kanal = "SMS", tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("222", FERDIGSTILT, kanal = "SMS", tidspunkt = nowAtUtc().plusDays(1)))
        testRapid.sendMessageAsJson(eksternVarslingStatus("222", FERDIGSTILT, kanal = "EPOST", tidspunkt = nowAtUtc().plusDays(1)))

        testRapid.sendMessageAsJson(aktiverVarselEvent("oppgave", "333"))
        testRapid.sendMessageAsJson(eksternVarslingStatus("333", OVERSENDT, tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("333", FEILET, tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("333", FERDIGSTILT, kanal = "SMS", tidspunkt = nowAtUtc().plusDays(1)))

        testRapid.sendMessageAsJson(aktiverVarselEvent("oppgave", "444"))
        testRapid.sendMessageAsJson(eksternVarslingStatus("444", OVERSENDT, tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("444", FEILET, tidspunkt = nowAtUtc()))
        testRapid.sendMessageAsJson(eksternVarslingStatus("444", INFO, tidspunkt = nowAtUtc().plusDays(1)))

        val status1 = varselRepository.getVarsel("111")?.eksternVarslingStatus
        status1!!.sendt shouldBe true
        status1.renotifikasjonSendt shouldBe false

        val status2 = varselRepository.getVarsel("222")?.eksternVarslingStatus
        status2!!.sendt shouldBe true
        status2.renotifikasjonSendt shouldBe true

        val status3 = varselRepository.getVarsel("333")?.eksternVarslingStatus
        status3!!.sendt shouldBe true
        status3.renotifikasjonSendt shouldBe true

        val status4 = varselRepository.getVarsel("444")?.eksternVarslingStatus
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

    private fun setupEksternVarslingStatusSink(testRapid: TestRapid) =
        EksternVarslingStatusSink(
            rapidsConnection = testRapid,
            eksternVarslingStatusUpdater = eksternVarslingStatusUpdater,
        )

    private fun setupVarselSink(testRapid: TestRapid) = AktiverVarselSink(
        rapidsConnection = testRapid,
        varselRepository = varselRepository,
        varselAktivertProducer = mockk(relaxed = true)
    )
}

private fun TestRapid.sendMessageAsJson(message: Any) = sendTestMessage(message.toJson(includeNull = true))

private fun MockProducer<String, String>.verifyOutput(verifier: (List<JsonNode>) -> Unit) {
    val objectMapper = jacksonObjectMapper()
    history().map { it.value() }.map { objectMapper.readTree(it) }.let(verifier)
}
