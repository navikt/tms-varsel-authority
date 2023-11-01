package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.toJson
import no.nav.tms.varsel.authority.write.aktiver.*
import no.nav.tms.varsel.authority.write.aktiver.AktiverVarselSink
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InaktiverVarselSinkLegacyTest {
    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val aktivertProducer = VarselAktivertProducer(kafkaProducer = mockProducer, topicName = "testtopic")
    private val inaktivertProducer = VarselInaktivertProducer(kafkaProducer = mockProducer, topicName = "testtopic")

    private val testRapid = TestRapid()
    private val database = LocalPostgresDatabase.cleanDb()
    private val repository = WriteVarselRepository(database)

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .build()

    @BeforeEach
    fun setup() {
        AktiverVarselSink(testRapid, repository, aktivertProducer)
        DoneEventSink(testRapid, repository, inaktivertProducer)
    }

    @AfterEach
    fun cleanUp() {
        mockProducer.clear()
        database.update {
            queryOf("delete from varsel")
        }
    }

    @Test
    fun `inaktiverer varsel`() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        val inaktiverEvent = inaktiverVarselEvent("123")

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktiverEvent.toJson())

        val dbVarsel = repository.getVarsel(beskjedEvent.eventId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.aktiv shouldBe false
        dbVarsel.inaktivert.shouldNotBeNull()

        val outputJson = mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .first()

        outputJson["varselId"].asText() shouldBe beskjedEvent.eventId
        outputJson["varselType"].asText() shouldBe "beskjed"
    }

    @Test
    fun `ignorer inaktiver-event hvis varsler allerede er inaktivt`() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        val inaktiverEvent = inaktiverVarselEvent("123")

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktiverEvent.toJson())

        val inaktivertTid = repository.getVarsel(beskjedEvent.eventId)!!.inaktivert

        testRapid.sendTestMessage(inaktiverEvent.toJson())

        repository.getVarsel(beskjedEvent.eventId)!!.inaktivert shouldBe inaktivertTid

        mockProducer.history()            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .size shouldBe 1
    }
}
