package no.nav.tms.varsel.authority.write.eksternvarsling

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import kotliquery.queryOf
import no.nav.tms.kafka.application.MessageBroadcaster
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.inaktiver.VarselNotFoundException
import no.nav.tms.varsel.authority.write.opprett.OpprettVarselSubscriber
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import no.nav.tms.varsel.authority.write.opprett.opprettVarselEvent
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class EksternVarslingStatusOppdatertSubscriberTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val varselRepository = WriteVarselRepository(database)

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val eksternVarslingStatusRepository = EksternVarslingStatusRepository(database)
    private val eksternVarslingStatusUpdater =
        EksternVarslingStatusUpdater(
            eksternVarslingStatusRepository,
            varselRepository
        )

    private val testBroadcaster =
        MessageBroadcaster(
            EksternVarslingStatusOppdatertSubscriber(eksternVarslingStatusUpdater = eksternVarslingStatusUpdater),
            OpprettVarselSubscriber(
                varselRepository = varselRepository,
                varselAktivertProducer = mockk(relaxed = true)
            ),
            enableTracking = true
        )

    @BeforeEach
    fun resetDb() {
        database.update { queryOf("delete from varsel") }
        mockProducer.clear()
        testBroadcaster.clearHistory()
    }

    @Test
    fun `lagrer forenklet info om hvorvidt ekstern varsling er sendt`() {

        val kanal = "EPOST"

        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val oppdatertEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = kanal,
            renotifikasjon = false,
            batch = false,
            feilmelding = null
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(oppdatertEvent)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe true
            it.renotifikasjonSendt shouldBe false
            it.sendtSomBatch shouldBe false
            it.sisteStatus shouldBe EksternStatus.Sendt

            it.kanaler shouldContain kanal
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `lagrer forenklet info om feil ved ekstern varsling`() {

        val feilmelding1 = "noe har feilet"
        val feilmelding2 = "noe har feilet igjen"

        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val varslingFeilet1 = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Feilet,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = feilmelding1
        )
        val varslingFeilet2 = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Feilet,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = feilmelding2
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(varslingFeilet1)
        testBroadcaster.broadcastJson(varslingFeilet2)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe false
            it.renotifikasjonSendt shouldBe false
            it.sendtSomBatch shouldBe false

            it.kanaler.size shouldBe 0

            it.feilhistorikk.size shouldBe 2
            it.feilhistorikk[0].feilmelding shouldBe feilmelding1
            it.feilhistorikk[1].feilmelding shouldBe feilmelding2
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `ignorerer hendelser for andre statuser`() {

        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val bestiltEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Bestilt,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = null
        )
        val infoEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Info,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = null
        )
        val ferdigstiltEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Ferdigstilt,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = null
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(bestiltEvent)
        testBroadcaster.broadcastJson(infoEvent)
        testBroadcaster.broadcastJson(ferdigstiltEvent)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus.shouldBeNull()

        testBroadcaster.history().collectAggregate(EksternVarslingStatusOppdatertSubscriber::class).let {
            it.shouldNotBeNull()
            it.ignored shouldBe 4
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `lagrer info om revarsling`() {

        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val oppdatertEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "EPOST",
            renotifikasjon = true,
            batch = false,
            feilmelding = null
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(oppdatertEvent)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe true
            it.renotifikasjonSendt shouldBe true
            it.sendtSomBatch shouldBe false
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `lagrer info om ekstern varsling ble sendt som del av batch`() {

        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val oppdatertEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "EPOST",
            renotifikasjon = false,
            batch = true,
            feilmelding = null
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(oppdatertEvent)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe true
            it.renotifikasjonSendt shouldBe false
            it.sendtSomBatch shouldBe true
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `gjør ingenting hvis varselId er ukjent`() {

        val varselId = UUID.randomUUID().toString()

        val event = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "EPOST",
            renotifikasjon = false,
            batch = false,
            feilmelding = null
        )

        testBroadcaster.broadcastJson(event)

        testBroadcaster.history().findFailedOutcome(EksternVarslingStatusOppdatertSubscriber::class) {
            it["varselId"].asText() == varselId
        }.let {
            it.shouldNotBeNull()
            it.cause::class shouldBe UpdatedVarselMissingException::class
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `er idempotent ved innlesing av flere statuser for samme varsel`() {
        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val sendtEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "EPOST",
            renotifikasjon = false,
            batch = false,
            feilmelding = null
        )
        val revarsletEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "SMS",
            renotifikasjon = true,
            batch = false,
            feilmelding = null
        )
        val batchetEvent = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "EPOST",
            renotifikasjon = false,
            batch = true,
            feilmelding = null
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(batchetEvent)
        testBroadcaster.broadcastJson(revarsletEvent)
        testBroadcaster.broadcastJson(sendtEvent)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus.shouldNotBeNull()

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.sendt shouldBe true
            it.renotifikasjonSendt shouldBe true
            it.sendtSomBatch shouldBe true

            it.kanaler.size shouldBe 2
            it.kanaler shouldContainAll listOf("SMS", "EPOST")
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `ignorerer duplikate feil`() {
        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val feilet1 = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Feilet,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = "En feilmelding"
        )
        val feilet2 = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Feilet,
            kanal = null,
            renotifikasjon = false,
            batch = false,
            feilmelding = "En annen feilmelding"
        )

        testBroadcaster.broadcastJson(varselEvent)
        testBroadcaster.broadcastJson(feilet1)
        testBroadcaster.broadcastJson(feilet1)
        testBroadcaster.broadcastJson(feilet2)
        testBroadcaster.broadcastJson(feilet1)

        val dbVarsel = varselRepository.getVarsel(varselId)

        dbVarsel?.eksternVarslingStatus shouldNotBe null

        dbVarsel?.eksternVarslingStatus?.let {
            it shouldNotBe null

            it.feilhistorikk.size shouldBe 2
        }

        mockProducer.history().size shouldBe 0
    }

    @Test
    fun `lagrer info om siste status`() {
        val varselId = UUID.randomUUID().toString()

        val varselEvent = opprettVarselEvent("beskjed", varselId)
        val venter = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Venter
        )
        val sendt = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Sendt,
            kanal = "SMS",
            renotifikasjon = false,
            batch = false
        )
        val kansellert = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Kansellert,
            batch = false
        )
        val feilet = eksternVarslingOppdatert(
            varselId = varselId,
            status = EksternStatus.Feilet,
            batch = false,
            feilmelding = "En feilmelding"
        )

        testBroadcaster.broadcastJson(varselEvent)

        testBroadcaster.broadcastJson(venter)
        varselRepository.getVarsel(varselId)
            ?.eksternVarslingStatus
            ?.also { it.shouldNotBeNull() }
            ?.let {
                it.sisteStatus shouldBe EksternStatus.Venter
            }

        testBroadcaster.broadcastJson(sendt)
        varselRepository.getVarsel(varselId)
            ?.eksternVarslingStatus
            ?.also { it.shouldNotBeNull() }
            ?.let {
                it.sisteStatus shouldBe EksternStatus.Sendt
            }

        testBroadcaster.broadcastJson(kansellert)
        varselRepository.getVarsel(varselId)
            ?.eksternVarslingStatus
            ?.also { it.shouldNotBeNull() }
            ?.let {
                it.sisteStatus shouldBe EksternStatus.Kansellert
            }

        testBroadcaster.broadcastJson(feilet)
        varselRepository.getVarsel(varselId)
            ?.eksternVarslingStatus
            ?.also { it.shouldNotBeNull() }
            ?.let {
                it.sisteStatus shouldBe EksternStatus.Feilet
            }
    }
}
