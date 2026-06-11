package no.nav.tms.varsel.authority.write.opprett

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.tms.kafka.application.MessageBroadcaster
import no.nav.tms.varsel.action.EksternVarslingBestilling
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.mockProducer
import no.nav.tms.varsel.authority.shouldBeSameTime
import no.nav.tms.varsel.authority.write.outgoing.RecordQueueRepository
import no.nav.tms.varsel.authority.write.outgoing.QueueableKafkaProducer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

class OpprettVarselSubscriberTest {

    private val database = LocalPostgresDatabase.cleanDb()

    private val mockProducer = mockProducer()
    private val recordQueueRepository = RecordQueueRepository(database)
    private val kafkaProducer = QueueableKafkaProducer(recordQueueRepository, mockProducer)

    private val aktivertProducer = VarselOpprettetProducer(kafkaProducer = kafkaProducer, topicName = "testtopic")
    private val repository = WriteVarselRepository(database)
    private val testBroadcaster = MessageBroadcaster(OpprettVarselSubscriber(repository, aktivertProducer), enableTracking = true)

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .build()

    @AfterEach
    fun cleanUp() {
        mockProducer.clear()
        mockProducer.sendException = null
        database.update {
            queryOf("delete from varsel")
        }
        LocalPostgresDatabase.cleanDb()
        testBroadcaster.clearHistory()
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

        recordQueueRepository.peekNext(50)
            .map { objectMapper.readTree(it.recordValue) }
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
                varselAktivert["eksternVarslingBestilling"]["utsettSendingTil"].asText() shouldBeSameTime opprettJson["eksternVarsling"]["utsettSendingTil"].asText()

            }
    }

    @Test
    fun `forkaster opprett-event hvis validering feiler`() {
        val varselId = "bad_id"

        val opprettBeskjedEvent = opprettVarselEvent("beskjed", varselId)

        testBroadcaster.broadcastJson(opprettBeskjedEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldBeNull()

        testBroadcaster.history().findFailedOutcome(OpprettVarselSubscriber::class) {
            it["varselId"].asText() == varselId
        }.let {
            it.shouldNotBeNull()
            it.cause::class shouldBe OpprettVarselSubscriber.OpprettVarselValidationException::class
        }

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

        testBroadcaster.history().findFailedOutcome(OpprettVarselSubscriber::class) {
            it["varselId"].asText() == varselId
        }.let {
            it.shouldNotBeNull()
            it.cause::class shouldBe OpprettVarselSubscriber.DuplikatVarselException::class
        }

        recordQueueRepository.peekNext(10).size shouldBe 1

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
            "innboks", varselIdInnboks, eksternVarsling = EksternVarslingBestilling(kanBatches = null), aktivFremTil = null,
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

        recordQueueRepository.peekNext(50)
            .map { objectMapper.readTree(it.recordValue) }
            .find { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "beskjed"}
            .let { it.shouldNotBeNull() }
            .let { varselAktivert ->
                varselAktivert["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe true
            }

        recordQueueRepository.peekNext(50)
            .map { objectMapper.readTree(it.recordValue) }
            .find { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "oppgave"}
            .let { it.shouldNotBeNull() }
            .let { varselAktivert ->
                varselAktivert["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe false
            }

        recordQueueRepository.peekNext(50)
            .map { objectMapper.readTree(it.recordValue) }
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

        recordQueueRepository.peekNext(500)
            .map { objectMapper.readTree(it.recordValue) }
            .filter { it["@event_name"].asText() == "opprettet" && it["type"].asText() == "beskjed"}
            .also { it.isEmpty() shouldBe false }
            .forEach { varselOpprettet ->
                varselOpprettet["eksternVarslingBestilling"]["kanBatches"].asBoolean() shouldBe false
            }
    }

    @Test
    fun `takler dårlig data på topic`() {
        val varselId = randomUUID().toString()

        val feilaktigEvent = """
            {
              "@event_name": "opprett",
              "type": "innboks",
              "varselId": "$varselId",
              "ident": "01234567890",
              "tekster": [
                {
                  "spraakkode": "nb",
                  "tekst": "Test",
                  "default": true
                }
              ],
              "link": "https://www.nav.no/min-side",
              "sensitivitet": "feil",
              "eksternVarsling": {
                "prefererteKanaler": [
                  "Nix"
                ]
              },
              "produsent": {
                "cluster": "test",
                "namespace": "test",
                "appnavn": "test"
              }
            }
        """.trimIndent()

        shouldNotThrow<Exception> {
            testBroadcaster.broadcastJson(feilaktigEvent)
        }

        testBroadcaster.history().findFailedOutcome(OpprettVarselSubscriber::class) {
            it["varselId"].asText() == varselId
        }.let {
            it.shouldNotBeNull()
            it.cause::class shouldBe OpprettVarselSubscriber.OpprettVarselDeserializationException::class
        }
    }
}

