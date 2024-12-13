package no.nav.tms.varsel.authority.write.arkiv

import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotliquery.queryOf
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.asZonedDateTime
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.config.PodLeaderElection
import no.nav.tms.varsel.authority.config.defaultObjectMapper
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration.ofMinutes
import java.time.ZonedDateTime


internal class PeriodicVarselArchiverTest {

    private val database = LocalPostgresDatabase.cleanDb()
    private val archiveRepository = VarselArkivRepository(database)
    private val leaderElection: PodLeaderElection = mockk()

    private val testRepository = ArchiveTestRepository(database)

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val arkivertProducer = VarselArkivertProducer(mockProducer ,"testTopic")
    private val gammelBeskjed =
        varsel(type = Beskjed, varselId = "b1", opprettet = nowAtUtc().minusDays(11))
    private val nyBeskjed =
        varsel(type = Beskjed, varselId = "b2", opprettet = nowAtUtc().minusDays(9))

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
        database.update { queryOf("delete from varsel_arkiv") }
    }


    fun createVarsel(vararg varsler: DatabaseVarsel) {
        val varselRepository = WriteVarselRepository(database)

        varsler.forEach { varselRepository.insertVarsel(it) }
    }

    @Test
    fun `arkiverer alle gamle varsler`() {

        coEvery { leaderElection.isLeader() } returns true

        runArchiverUntilNRemains(1)

        val arkiverteVarsler = testRepository.getAllArchivedVarsel()
        arkiverteVarsler.size shouldBe 1
        arkiverteVarsler.first().apply {
            type shouldBe Beskjed
            varselId shouldBe gammelBeskjed.varselId
        }

        mockProducer.history().size shouldBe 1

        mockProducer.history()
            .map { it.value() }
            .map { objectMapper.readTree(it) }
            .first()
            .let {
                it["varselId"].asText() shouldBe gammelBeskjed.varselId
                it["varseltype"].asText() shouldBe gammelBeskjed.type.name.lowercase()
                it["produsent"]["cluster"]?.asText() shouldBe gammelBeskjed.produsent.cluster
                it["produsent"]["namespace"].asText() shouldBe gammelBeskjed.produsent.namespace
                it["produsent"]["appnavn"].asText() shouldBe gammelBeskjed.produsent.appnavn
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
            varselId shouldBe gammelBeskjed.varselId
            innhold.tekst shouldBe gammelBeskjed.innhold.tekst
            innhold.link shouldBe gammelBeskjed.innhold.link
            sensitivitet shouldBe gammelBeskjed.sensitivitet
            aktiv shouldBe gammelBeskjed.aktiv
            produsent.appnavn shouldBe gammelBeskjed.produsent.appnavn
            produsent.namespace shouldBe gammelBeskjed.produsent.namespace
            eksternVarslingStatus?.kanaler shouldBe gammelBeskjed.eksternVarslingStatus?.kanaler
            eksternVarslingStatus?.sendt shouldBe gammelBeskjed.eksternVarslingStatus?.sendt
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
        type: Varseltype,
        opprettet: ZonedDateTime,
    ) = DatabaseVarsel(
        type = type,
        varselId = varselId,
        aktiv = true,
        ident = "123",
        sensitivitet = Sensitivitet.Substantial,
        innhold = Innhold(
            tekst = "Bla.",
            link = "http://link",
        ),
        produsent = DatabaseProdusent(namespace = "namespace", appnavn = "app", cluster = null),
        eksternVarslingStatus = EksternVarslingStatus(
            sendt = true,
            renotifikasjonSendt = true,
            kanaler = listOf("EPOST"),
            sistOppdatert = nowAtUtc()
        ),
        opprettet = opprettet
    )
}


