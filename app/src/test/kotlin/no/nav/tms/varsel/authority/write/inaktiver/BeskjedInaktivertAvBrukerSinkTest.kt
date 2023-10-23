package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.tms.varsel.authority.LocalDateTimeHelper
import no.nav.tms.varsel.authority.LocalPostgresDatabase
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.toJson
import no.nav.tms.varsel.authority.write.aktiver.*
import no.nav.tms.varsel.authority.write.aktiver.AktiverVarselSink
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class BeskjedInaktivertAvBrukerSinkTest {

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
        InaktiverVarselSink(testRapid, repository, inaktivertProducer)
        BeskjedInaktivertAvBrukerSink(testRapid, repository, inaktivertProducer)
    }

    @AfterEach
    fun cleanUp() {
        mockProducer.clear()
        database.update {
            queryOf("delete from varsel")
        }
    }

    @Test
    fun `inaktiverer varsel inaktivert av bruker i aggregator`() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        val inaktivertEvent = inaktivertEvent("123", VarselInaktivertKilde.Bruker)

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktivertEvent.toJson())

        val dbVarsel = repository.getVarsel(beskjedEvent.eventId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.aktiv shouldBe false
        dbVarsel.inaktivert.shouldNotBeNull()
        dbVarsel.inaktivertAv shouldBe VarselInaktivertKilde.Bruker

        val outputJson = mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .first()

        outputJson["varselId"].asText() shouldBe beskjedEvent.eventId
        outputJson["varselType"].asText() shouldBe "beskjed"
        outputJson["kilde"].asText() shouldBe "bruker"
    }

    @Test
    fun `ignorerer andre inaktivert-kilder`() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        val inaktivertAvProdusent = inaktivertEvent("123", VarselInaktivertKilde.Produsent)
        val inaktivertAvFrist = inaktivertEvent("123", VarselInaktivertKilde.Frist)

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktivertAvProdusent.toJson())
        testRapid.sendTestMessage(inaktivertAvFrist.toJson())

        val dbVarsel = repository.getVarsel(beskjedEvent.eventId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.aktiv shouldBe true

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .size shouldBe 0
    }

    @Test
    fun `ignorerer egne inaktivert-eventer`() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        val inaktiverEvent = inaktiverVarselEvent("123")

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktiverEvent.toJson())

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .size shouldBe 1
    }

    @Test
    fun `ignorer inaktiver-event hvis varsler allerede er inaktivt`() {
        val beskjedEvent = aktiverVarselEvent("beskjed", "123")

        val inaktivertEvent = inaktivertEvent("123", VarselInaktivertKilde.Bruker)

        testRapid.sendTestMessage(beskjedEvent.toJson())
        testRapid.sendTestMessage(inaktivertEvent.toJson())

        val inaktivertTid = repository.getVarsel(beskjedEvent.eventId)!!.inaktivert

        testRapid.sendTestMessage(inaktivertEvent.toJson())

        repository.getVarsel(beskjedEvent.eventId)!!.inaktivert shouldBe inaktivertTid

        mockProducer.history()            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .size shouldBe 1
    }
}

private fun inaktivertEvent(varselId: String, kilde: VarselInaktivertKilde) = InaktivertAvAggregatorEvent(
    eventId = varselId,
    varselType = Varseltype.Beskjed,
    namespace = "namespace",
    appnavn = "appnavn",
    kilde = kilde,
    tidspunkt = LocalDateTimeHelper.nowAtUtc(),
)

private data class InaktivertAvAggregatorEvent(
    val eventId: String,
    val varselType: Varseltype,
    val namespace: String,
    val appnavn: String,
    val kilde: VarselInaktivertKilde,
    val tidspunkt: LocalDateTime
) {
    @JsonProperty("@event_name") val eventName = "inaktivert"
    @JsonProperty("@source") val source = "aggregator"
}
