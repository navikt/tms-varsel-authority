package no.nav.tms.varsel.authority.write.aktiver

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.authority.LocalPostgresDatabase
import no.nav.tms.varsel.authority.config.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.toJson
import no.nav.tms.varsel.authority.write.inaktiver.InaktiverVarselSink
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class InaktiverVarselSinkTest {
    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val aktivertProducer = VarselAktivertProducer(kafkaProducer = mockProducer, topicName = "testtopic")
    private val inaktivertProducer = VarselInaktivertProducer(kafkaProducer = mockProducer, topicName = "testtopic")
    private val metricsReporter: VarselMetricsReporter = mockk(relaxed = true)

    private val testRapid = TestRapid()
    private val database = LocalPostgresDatabase.cleanDb()
    private val repository = WriteVarselRepository(database)

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .build()

    @BeforeEach
    fun setup() {
        AktiverVarselSink(testRapid, repository, aktivertProducer, metricsReporter)
        InaktiverVarselSink(testRapid, repository, inaktivertProducer, metricsReporter)
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

        val inaktiverEvent = inaktiverVarselEvent("123")

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktiverEvent.toJson())

        val dbVarsel = repository.getVarsel(beskjedEvent.eventId)

        dbVarsel shouldNotBe null

        dbVarsel!!.aktiv shouldBe false
        dbVarsel.inaktivert shouldNotBe null

        val outputJson = mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .first()

        outputJson["varselId"].asText() shouldBe beskjedEvent.eventId
        outputJson["varselType"].asText() shouldBe "beskjed"
    }
}
