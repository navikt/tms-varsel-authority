package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import java.util.UUID.randomUUID

class OpprettVarselSinkTest {

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val rapidProducer = VarselAktivertProducer(kafkaProducer = mockProducer, topicName = "testtopic")

    private val testRapid = TestRapid()
    private val database = LocalPostgresDatabase.cleanDb()
    private val repository = WriteVarselRepository(database)

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .build()

    @BeforeEach
    fun setup() {
        OpprettVarselSink(testRapid, repository, rapidProducer)
    }

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

        val opprettBeskjedEvent = opprettVarselEvent("beskjed", varselId)
        val opprettJson = objectMapper.readTree(opprettBeskjedEvent)

        testRapid.sendTestMessage(opprettBeskjedEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.type.lowercaseName shouldBe "beskjed"
        dbVarsel.innhold.tekst shouldBe opprettJson["tekster"].first()["tekst"].asText()
        dbVarsel.innhold.link shouldBe opprettJson["link"].asText()
        dbVarsel.aktiv shouldBe true
        dbVarsel.ident shouldBe opprettJson["ident"].asText()
        dbVarsel.produsent.cluster shouldBe opprettJson["produsent"]["cluster"].asText()
        dbVarsel.produsent.namespace shouldBe opprettJson["produsent"]["namespace"].asText()
        dbVarsel.produsent.appnavn shouldBe opprettJson["produsent"]["appnavn"].asText()
        dbVarsel.inaktivert shouldBe null

        val varselAktivert = mockProducer.history()
            .first()
            .value()
            .let { objectMapper.readTree(it) }

        varselAktivert["varselId"].asText() shouldBe varselId
        varselAktivert["type"].asText() shouldBe "beskjed"
        varselAktivert["innhold"]["tekst"].asText() shouldBe opprettJson["tekster"].first()["tekst"].asText()
        varselAktivert["innhold"]["link"].asText() shouldBe opprettJson["link"].asText()
    }

    @Test
    fun `forkaster opprett-event hvis validering feiler`() {
        val varselId = "bad_id"

        val opprettBeskjedEvent = opprettVarselEvent("beskjed", varselId)

        testRapid.sendTestMessage(opprettBeskjedEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldBeNull()

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `forkaster opprett-event hvis varselid er duplikat`() {
        val varselId = randomUUID().toString()

        val opprettEvent = opprettVarselEvent("oppgave", varselId)
        val duplikatEvent = opprettVarselEvent("beskjed", varselId)

        testRapid.sendTestMessage(opprettEvent)
        testRapid.sendTestMessage(duplikatEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldNotBeNull()
        dbVarsel.type shouldBe Varseltype.Oppgave

        mockProducer.history().size shouldBe 1
    }
}
