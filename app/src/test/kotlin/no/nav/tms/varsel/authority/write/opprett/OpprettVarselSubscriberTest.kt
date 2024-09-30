package no.nav.tms.varsel.authority.write.opprett

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.tms.kafka.application.MessageBroadcaster
import no.nav.tms.varsel.action.EksternVarslingBestilling
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class OpprettVarselSubscriberTest {

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val aktivertProducer = VarselOpprettetProducer(kafkaProducer = mockProducer, topicName = "testtopic")
    private val database = LocalPostgresDatabase.cleanDb()
    private val repository = WriteVarselRepository(database)
    private val testBroadcaster = MessageBroadcaster(listOf(OpprettVarselSubscriber(repository, aktivertProducer)))

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .build()

    @AfterEach
    fun cleanUp() {
        mockProducer.clear()
        database.update {
            queryOf("delete from varsel")
        }
    }

    @Test
    fun `aktiverer varsler`() {
        val varselId = randomUUID().toString()

        val opprettBeskjedEvent = opprettVarselEvent(
            "beskjed", varselId, ekstraMetadada = """
            ,"beredskap_tittel":"something"
            
        """.trimIndent()
        )
        val opprettJson = objectMapper.readTree(opprettBeskjedEvent)

        testBroadcaster.broadcastJson(opprettBeskjedEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.type.name.lowercase() shouldBe "beskjed"
        dbVarsel.innhold.tekst shouldBe opprettJson["tekster"].first()["tekst"].asText()
        dbVarsel.innhold.link shouldBe opprettJson["link"].asText()
        dbVarsel.aktiv shouldBe true
        dbVarsel.ident shouldBe opprettJson["ident"].asText()
        dbVarsel.produsent.cluster shouldBe opprettJson["produsent"]["cluster"].asText()
        dbVarsel.produsent.namespace shouldBe opprettJson["produsent"]["namespace"].asText()
        dbVarsel.produsent.appnavn shouldBe opprettJson["produsent"]["appnavn"].asText()
        dbVarsel.inaktivert shouldBe null

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .find { it["@event_name"].asText() == "opprettet" }
            .let { it.shouldNotBeNull() }
            .let { varselAktivert ->
                varselAktivert["varselId"].asText() shouldBe varselId
                varselAktivert["type"].asText() shouldBe "beskjed"
                varselAktivert["innhold"]["tekst"].asText() shouldBe opprettJson["tekster"].first()["tekst"].asText()
                varselAktivert["innhold"]["link"].asText() shouldBe opprettJson["link"].asText()
                varselAktivert["innhold"]["tekster"].size() shouldBe opprettJson["tekster"].size()
                varselAktivert["innhold"]["tekster"].first()["tekst"].asText() shouldBe opprettJson["tekster"].first()["tekst"].asText()
                varselAktivert["innhold"]["tekster"].first()["spraakkode"].asText() shouldBe opprettJson["tekster"].first()["spraakkode"].asText()
                varselAktivert["innhold"]["tekster"].first()["default"].asBoolean() shouldBe opprettJson["tekster"].first()["default"].asBoolean()
                varselAktivert["produsent"]["cluster"].asText() shouldBe "cluster"
                varselAktivert["produsent"]["namespace"].asText() shouldBe "namespace"
                varselAktivert["produsent"]["appnavn"].asText() shouldBe "appnavn"
                varselAktivert["metadata"]["beredskap_tittel"].asText() shouldBe "something"
                varselAktivert["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe opprettJson["eksternVarsling"]["kanBatches"].asBoolean()
                varselAktivert["eksternVarslingBestilling"]["utsettSendingTil"].asText() shouldBe opprettJson["eksternVarsling"]["utsettSendingTil"].asText()

            }
    }

    @Test
    fun `forkaster opprett-event hvis validering feiler`() {
        val varselId = "bad_id"

        val opprettBeskjedEvent = opprettVarselEvent("beskjed", varselId)

        testBroadcaster.broadcastJson(opprettBeskjedEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldBeNull()

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `forkaster opprett-event hvis varselid er duplikat`() {
        val varselId = randomUUID().toString()

        val opprettEvent = opprettVarselEvent("oppgave", varselId)
        val duplikatEvent = opprettVarselEvent("beskjed", varselId)

        testBroadcaster.broadcastJson(opprettEvent)
        testBroadcaster.broadcastJson(duplikatEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldNotBeNull()
        dbVarsel.type shouldBe Varseltype.Oppgave

        mockProducer.history().size shouldBe 1
    }

    @Test
    fun `setter default verdi på kanBatches når det ikke er satt`() {
        val varselIdBeskjed = randomUUID().toString()
        val varselIdInnboks = randomUUID().toString()
        val varselIdOppgave = randomUUID().toString()

        val opprettBeskjedEvent = opprettVarselEvent(
            "beskjed", varselIdBeskjed, eksternVarsling = EksternVarslingBestilling(kanBatches = null)
        )

        val opprettInnboksEvent = opprettVarselEvent(
            "innboks", varselIdInnboks, eksternVarsling = EksternVarslingBestilling(kanBatches = null)
        )
        val opprettOppgaveEvent = opprettVarselEvent(
            "oppgave", varselIdOppgave, eksternVarsling = EksternVarslingBestilling(kanBatches = null)
        )

        testBroadcaster.broadcastJson(opprettBeskjedEvent)
        testBroadcaster.broadcastJson(opprettInnboksEvent)
        testBroadcaster.broadcastJson(opprettOppgaveEvent)

        val dbBeskjed = repository.getVarsel(varselIdBeskjed)
        val dbInnboks = repository.getVarsel(varselIdInnboks)
        val dbOppgave = repository.getVarsel(varselIdOppgave)

        dbBeskjed.shouldNotBeNull()
        dbInnboks.shouldNotBeNull()
        dbOppgave.shouldNotBeNull()

        dbBeskjed.eksternVarslingBestilling?.kanBatches shouldBe true
        dbInnboks.eksternVarslingBestilling?.kanBatches shouldBe false
        dbOppgave.eksternVarslingBestilling?.kanBatches shouldBe false

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .find { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "beskjed"}
            .let { it.shouldNotBeNull() }
            .let { varselAktivert ->
                varselAktivert["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe true
            }

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .find { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "oppgave"}
            .let { it.shouldNotBeNull() }
            .let { varselAktivert ->
                varselAktivert["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe false
            }

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .find { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "innboks"}
            .let { it.shouldNotBeNull() }
            .let { varselAktivert ->
                varselAktivert["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe false
            }
    }

    @Test
    fun `default verdi på kanBatches for beskjed er false hvis sms- eller epost-tekst er annet enn standard`() {
        val varselIdSms = randomUUID().toString()
        val varselIdEpost = randomUUID().toString()

        val opprettBeskjedMedSms = opprettVarselEvent(
            "beskjed", varselIdSms, eksternVarsling = EksternVarslingBestilling(kanBatches = null, smsVarslingstekst = "Annet")
        )

        val opprettBeskjedMedEpost = opprettVarselEvent(
            "beskjed", varselIdEpost, eksternVarsling = EksternVarslingBestilling(kanBatches = null, epostVarslingstekst = "Annet")
        )

        testBroadcaster.broadcastJson(opprettBeskjedMedSms)
        testBroadcaster.broadcastJson(opprettBeskjedMedEpost)

        val dbBeskjedMedSms = repository.getVarsel(varselIdSms)
        val dbBeskjedMedEpost = repository.getVarsel(varselIdEpost)

        dbBeskjedMedSms.shouldNotBeNull()
        dbBeskjedMedEpost.shouldNotBeNull()

        dbBeskjedMedSms.eksternVarslingBestilling?.kanBatches shouldBe false
        dbBeskjedMedEpost.eksternVarslingBestilling?.kanBatches shouldBe false

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "beskjed"}
            .also { it.isEmpty() shouldBe false }
            .forEach { varselOpprettet ->
                varselOpprettet["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe false
            }

    }
}

