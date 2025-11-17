package no.nav.tms.varsel.authority.write.expiry

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotliquery.queryOf
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime

internal class PeriodicExpiredVarselProcessorTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val varselRepository = WriteVarselRepository(database)

    private val varselInaktivertProducer = mockk<VarselInaktivertProducer>(relaxed = true)
    private val leaderElection = mockk<PodLeaderElection>(relaxed = true)

    private val expiredVarselRepository = ExpiredVarselRepository(database)
    private val expiredVarselProcessor =
        PeriodicExpiredVarselProcessor(
            expiredVarselRepository,
            varselInaktivertProducer,
            leaderElection
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

    private val inactiveVarsel = varsel(
        varselId = "b3",
        type = Beskjed,
        aktiv = false,
        aktivFremTil = pastDate
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
        varselRepository.insertVarsel(expiredBeskjed)
        varselRepository.insertVarsel(activeBeskjed)
        varselRepository.insertVarsel(inactiveVarsel)
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
    type: Varseltype,
    aktiv: Boolean = true,
    aktivFremTil: ZonedDateTime,
) = DatabaseVarsel(
    type = type,
    varselId = varselId,
    sensitivitet = Sensitivitet.Substantial,
    aktiv = aktiv,
    produsent = DatabaseProdusent("cluster", "namespace", "appname"),
    ident = "123",
    innhold = Innhold(
        tekst = "Bla.",
        link = "http://link",
    ),
    opprettet = nowAtUtc(),
    aktivFremTil = aktivFremTil
)

