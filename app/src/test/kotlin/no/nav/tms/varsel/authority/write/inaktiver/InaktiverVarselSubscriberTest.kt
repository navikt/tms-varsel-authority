package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.tms.kafka.application.MessageBroadcaster
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.opprett.OpprettVarselSubscriber
import no.nav.tms.varsel.authority.write.opprett.VarselOpprettetProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import no.nav.tms.varsel.authority.write.opprett.opprettVarselEvent
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID.randomUUID

internal class InaktiverVarselSubscriberTest {
    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val varselOpprettetProducer = VarselOpprettetProducer(kafkaProducer = mockProducer, topicName = "testtopic")
    private val inaktivertProducer = VarselInaktivertProducer(kafkaProducer = mockProducer, topicName = "testtopic")

    private val database = LocalPostgresDatabase.cleanDb()
    private val repository = WriteVarselRepository(database)
    private val testBroadcaster = MessageBroadcaster(
        OpprettVarselSubscriber(repository, varselOpprettetProducer),
        InaktiverVarselSubscriber(repository, inaktivertProducer),
        enableTracking = true
    )

    private val objectMapper = jacksonMapperBuilder()
        .addModule(JavaTimeModule())
        .build()


    @AfterEach
    fun cleanUp() {
        mockProducer.clear()
        database.update {
            queryOf("delete from varsel")
        }
        testBroadcaster.clearHistory()
    }

    @Test
    fun `inaktiverer varsel`() {
        val varselId = randomUUID().toString()
        val beskjedEvent = opprettVarselEvent("beskjed", varselId)
        val inaktiverEvent = inaktiverVarsel(varselId)

        testBroadcaster.broadcastJson(beskjedEvent)
        testBroadcaster.broadcastJson(inaktiverEvent)

        val dbVarsel = repository.getVarsel(varselId)

        dbVarsel.shouldNotBeNull()

        dbVarsel.aktiv shouldBe false
        dbVarsel.inaktivert.shouldNotBeNull()

        val outputJson = mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .first()

        outputJson["varselId"].asText() shouldBe varselId
        outputJson["varseltype"].asText() shouldBe "beskjed"
        outputJson["produsent"]["cluster"].asText() shouldBe "cluster"
        outputJson["produsent"]["namespace"].asText() shouldBe "namespace"
        outputJson["produsent"]["appnavn"].asText() shouldBe "appnavn"
    }

    @Test
    fun `ignorer inaktiver-event hvis varsler allerede er inaktivt`() {
        val varselId = randomUUID().toString()

        val beskjedEvent = opprettVarselEvent("beskjed", varselId)

        val inaktiverEvent = inaktiverVarsel(varselId)

        testBroadcaster.broadcastJson(beskjedEvent)
        testBroadcaster.broadcastJson(inaktiverEvent)

        val inaktivertTid = repository.getVarsel(varselId)!!.inaktivert

        testBroadcaster.broadcastJson(inaktiverEvent)

        repository.getVarsel(varselId)!!.inaktivert shouldBe inaktivertTid

        testBroadcaster.history().collectAggregate(InaktiverVarselSubscriber::class).let {
            it.shouldNotBeNull()
            it.accepted shouldBe 2
        }

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .size shouldBe 1
    }

    @Test
    fun `melder feil dersom inaktivert varsel ikke finnes`() {
        val varselId = randomUUID().toString()

        val inaktiverEvent = inaktiverVarsel(varselId)

        testBroadcaster.broadcastJson(inaktiverEvent)

        testBroadcaster.history().findFailedOutcome(InaktiverVarselSubscriber::class) {
            it["varselId"].asText() == varselId
        }.let {
            it.shouldNotBeNull()
            it.cause::class shouldBe InaktivertVarselMissingException::class
        }

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .filter { it["@event_name"].asText() == "inaktivert" }
            .size shouldBe 0
    }

    private fun inaktiverVarsel(varselId: String) = """
{
    "@event_name": "inaktiver",
    "varselId": "$varselId",
    "produsent": {
        "cluster": "cluster",
        "namespace": "namespace",
        "appnavn": "appnavn"
    },
    "metadata": {
        "built_at": "${nowAtUtc()}",
        "version": "test"
    }
}
       
    """.trimIndent()
}
