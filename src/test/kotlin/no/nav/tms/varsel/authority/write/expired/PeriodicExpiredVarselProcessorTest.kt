package no.nav.tms.varsel.authority.write.expired

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.done.VarselInaktivertProducer
import no.nav.tms.varsel.authority.election.LeaderElection
import no.nav.tms.varsel.authority.metrics.VarselMetricsReporter
import no.nav.tms.varsel.authority.write.sink.*
import no.nav.tms.varsel.authority.write.sink.VarselType.Beskjed
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

internal class PeriodicExpiredVarselProcessorTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val varselRepository = WriteVarselRepository(database)
    
    private val metricsReporter: VarselMetricsReporter = mockk(relaxed = true)
    private val varselInaktivertProducer = mockk<VarselInaktivertProducer>(relaxed = true)
    private val leaderElection = mockk<LeaderElection>(relaxed = true)

    private val expiredVarselRepository = ExpiredVarselRepository(database)
    private val expiredVarselProcessor =
        PeriodicExpiredVarselProcessor(
            expiredVarselRepository,
            varselInaktivertProducer,
            leaderElection,
            metricsReporter
        )

    private val pastDate = nowAtUtc().minusDays(7)
    private val futureDate = nowAtUtc().plusDays(7)

    private val expiredBeskjed = varsel(
        varselId = "b1",
        type = Beskjed,
        aktivFremTil = pastDate
    )

    private val activeBeskjed = varsel(
        varselId = "b2",
        type = Beskjed,
        aktivFremTil = futureDate
    )


    @AfterEach
    fun cleanUp() {
        database.update {
            queryOf("delete from varsel")
        }
        clearMocks(varselInaktivertProducer)
    }

    @BeforeEach
    fun setup() {
        varselRepository.createVarsel(expiredBeskjed)
        varselRepository.createVarsel(activeBeskjed)
    }

    @Test
    fun `Setter utgåtte varsler som inaktive`() {
        expiredVarselProcessor.updateExpiredVarsel()

        val unchangedBeskjed = varselRepository.getVarsel(activeBeskjed.varselId)

        val updatedBeskjed = varselRepository.getVarsel(expiredBeskjed.varselId)

        unchangedBeskjed?.aktiv shouldBe true
        unchangedBeskjed?.inaktivert shouldBe null

        updatedBeskjed?.inaktivert shouldNotBe null
        updatedBeskjed?.aktiv shouldBe false


        verify(exactly = 1) { varselInaktivertProducer.varselInaktivert(any()) }
    }
}

private fun varsel(
    varselId: String,
    type: VarselType,
    aktivFremTil: ZonedDateTime,
) = DatabaseVarsel(
    aktiv = true,
    produsent = Produsent("namespace", "appname"),
    varsel = Varsel(
        type = type,
        varselId = varselId,
        ident = "123",
        sikkerhetsnivaa = 3,
        tekst = "Bla.",
        link = "http://link",
    ),
    opprettet = nowAtUtc(),
    aktivFremTil = aktivFremTil
)

