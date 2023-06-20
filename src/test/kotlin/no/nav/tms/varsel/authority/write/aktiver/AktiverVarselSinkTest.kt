package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.assertions.show.show
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.authority.LocalPostgresDatabase
import no.nav.tms.varsel.authority.toJson
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class AktiverVarselSinkTest {

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
        AktiverVarselSink(testRapid, repository, rapidProducer)
    }

    @AfterEach
    fun cleanUp() {
        mockProducer.clear()
        database.update {
            queryOf("delete from varsel")
        }
    }

    @Test
    fun test() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        testRapid.sendTestMessage(beskjedEvent.toJson())

        val dbVarsel = repository.getVarsel(beskjedEvent.eventId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.type.lowercaseName shouldBe beskjedEvent.eventName
        dbVarsel.aktiv shouldBe true
        dbVarsel.ident shouldBe beskjedEvent.fodselsnummer
        dbVarsel.produsent.namespace shouldBe beskjedEvent.namespace
        dbVarsel.produsent.appnavn shouldBe beskjedEvent.appnavn
        dbVarsel.inaktivert shouldBe null

        val varselAktivert = mockProducer.history()
            .first()
            .value()
            .let { objectMapper.readTree(it) }

        varselAktivert["varselId"].asText() shouldBe beskjedEvent.eventId
        varselAktivert["type"].asText() shouldBe "beskjed"
    }
}
