package no.nav.tms.varsel.authority.archive

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.election.LeaderElection
import no.nav.tms.varsel.authority.sink.*
import no.nav.tms.varsel.authority.varsel.VarselType
import no.nav.tms.varsel.authority.varsel.VarselType.BESKJED
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.*
import java.time.Duration.ofMinutes
import java.time.ZonedDateTime


internal class PeriodicVarselArchiverTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val archiveRepository = VarselArchiveRepository(database)
    private val leaderElection: LeaderElection = mockk()

    private val testRepository = ArchiveTestRepository(database)

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val arkivertProducer = VarselArkivertProducer(mockProducer ,"testTopic")
    private val gammelBeskjed =
        varsel(type = BESKJED, varselId = "b1", opprettet = nowAtUtc().minusDays(11))
    private val nyBeskjed =
        varsel(type = BESKJED, varselId = "b2", opprettet = nowAtUtc().minusDays(9))

    private val objectMapper = defaultObjectMapper()

    @BeforeEach
    fun setup() {
        createVarsel(gammelBeskjed, nyBeskjed)
    }

    @AfterEach
    fun cleanUp() {
        clearMocks(leaderElection)
        mockProducer.clear()
        database.update { queryOf("delete from varsel") }
        database.update { queryOf("delete from varsel_archive") }
    }


    fun createVarsel(vararg varsler: DatabaseVarsel) {
        val varselRepository = VarselRepository(database)

        varsler.forEach { varselRepository.createVarsel(it) }
    }

    @Test
    fun `arkiverer alle gamle varsler`() {

        coEvery { leaderElection.isLeader() } returns true

        runArchiverUntilNRemains(1)

        val arkiverteVarsler = testRepository.getAllArchivedVarsel()
        arkiverteVarsler.size shouldBe 1
        arkiverteVarsler.first().apply {
            varsel.type shouldBe BESKJED.eventType
            varselId shouldBe gammelBeskjed.varselId
        }

        mockProducer.history().size shouldBe 1

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .first()
            .let {
                it["varselId"].asText() shouldBe gammelBeskjed.varselId
                it["varselType"].asText() shouldBe gammelBeskjed.type
                it["namespace"].asText() shouldBe gammelBeskjed.produsent.namespace
                it["appnavn"].asText() shouldBe gammelBeskjed.produsent.appnavn
                it["opprettet"].asZonedDateTime().toEpochSecond() shouldBe gammelBeskjed.opprettet.toEpochSecond()
        }
    }

    @Test
    fun `arkiverer beskjed-data`() = runBlocking<Unit> {

        coEvery { leaderElection.isLeader() } returns true

        runArchiverUntilNRemains(1)

        val arkiverteVarsler = testRepository.getAllArchivedVarsel()

        arkiverteVarsler.size shouldBe 1
        arkiverteVarsler.first().apply {
            varsel.ident shouldBe gammelBeskjed.ident
            varselId shouldBe gammelBeskjed.varselId
            varsel.tekst shouldBe gammelBeskjed.varsel.tekst
            varsel.link shouldBe gammelBeskjed.varsel.link
            varsel.sikkerhetsnivaa shouldBe gammelBeskjed.varsel.sikkerhetsnivaa
            aktiv shouldBe gammelBeskjed.aktiv
            produsent.appnavn shouldBe gammelBeskjed.produsent.appnavn
            produsent.namespace shouldBe gammelBeskjed.produsent.namespace
            eksternVarslingStatus?.kanaler shouldBe gammelBeskjed.eksternVarslingStatus?.kanaler
            eksternVarslingStatus?.eksternVarslingSendt shouldBe gammelBeskjed.eksternVarslingStatus?.eksternVarslingSendt
            opprettet shouldBe gammelBeskjed.opprettet
        }
    }

    @Test
    fun `does nothing when not leader`() = runBlocking {
        coEvery { leaderElection.isLeader() } returns false

        val archiver = PeriodicVarselArchiver(
            varselArchivingRepository = archiveRepository,
            ageThresholdDays = 10,
            interval = ofMinutes(10),
            leaderElection = leaderElection,
            varselArkivertProducer = arkivertProducer
        )

        archiver.start()
        delay(2000)
        archiver.stop()


        varselInDbCount() shouldBe 2
        testRepository.getAllArchivedVarsel().size shouldBe 0
        mockProducer.history().size shouldBe 0
    }

    private fun runArchiverUntilNRemains(remainingVarsler: Int = 0) = runBlocking {
        val archiver = PeriodicVarselArchiver(
            varselArchivingRepository = archiveRepository,
            ageThresholdDays = 10,
            interval = ofMinutes(10),
            leaderElection = leaderElection,
            varselArkivertProducer = arkivertProducer
        )

        archiver.start()
        delayUntilVarslerDeleted(remainingVarsler)
        archiver.stop()
    }

    private suspend fun delayUntilVarslerDeleted(remainingVarsler: Int = 0) {
        withTimeout(5000) {
            while (varselInDbCount() > remainingVarsler) {
                delay(100)
            }
        }
    }

    private fun varselInDbCount(): Int {
        return database.singleOrNull {
            queryOf("select count(*) as antall from varsel")
                .map { it.int("antall") }
                .asSingle
        }?: 0
    }

    private fun varsel(
        varselId: String,
        type: VarselType,
        opprettet: ZonedDateTime,
    ) = DatabaseVarsel(
        aktiv = true,
        varsel = Varsel(
            type = type.eventType,
            varselId = varselId,
            ident = "123",
            sikkerhetsnivaa = 3,
            tekst = "Bla.",
            link = "http://link",
        ),
        produsent = Produsent(namespace = "namespace", appnavn = "app"),
        eksternVarslingStatus = EksternVarslingStatus(
            eksternVarslingSendt = true,
            renotifikasjonSendt = true,
            kanaler = listOf("EPOST"),
            historikk = listOf(
                EksternVarslingHistorikkEntry(
                    melding = "Melding",
                    status = EksternStatus.Sendt,
                    distribusjonsId = 1L,
                    kanal = "EPOST",
                    renotifikasjon = false,
                    tidspunkt = nowAtUtc()
                )
            ),
            sistOppdatert = nowAtUtc()
        ),
        opprettet = opprettet
    )
}


