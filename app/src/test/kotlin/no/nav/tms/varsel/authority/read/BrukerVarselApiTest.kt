package no.nav.tms.varsel.authority.read

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import no.nav.tms.token.support.tokenx.validation.mock.LevelOfAssurance
import no.nav.tms.varsel.action.Sensitivitet.High
import no.nav.tms.varsel.action.Sensitivitet.Substantial
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.mockProducer
import org.junit.jupiter.api.AfterEach

import no.nav.tms.varsel.authority.read.Matchers.shouldFind
import no.nav.tms.varsel.authority.read.Matchers.shouldMatch
import no.nav.tms.varsel.authority.database.TestVarsel
import no.nav.tms.varsel.authority.database.testInnhold
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.junit.jupiter.api.Test

class BrukerVarselApiTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val mockProducer = mockProducer()

    private val inaktivertProducer = VarselInaktivertProducer(mockProducer, "topic")

    private val readRepository = ReadVarselRepository(database)
    private val writeRepository = WriteVarselRepository(database)
    private val varselInaktiverer = VarselInaktiverer(writeRepository, inaktivertProducer)

    private val ident = "123"

    @AfterEach
    fun deleteData() {
        LocalPostgresDatabase.cleanDb()
    }

    @Test
    fun `henter varsler for bruker`() = testVarselApi(userIdent = ident) { client ->
        val annenIdent = "456"

        val beskjed = TestVarsel(type = Beskjed, ident = ident, innhold = Innhold("Tekst uten lenke", null)).dbVarsel()
        val oppgave = TestVarsel(type = Oppgave, ident = ident).dbVarsel()
        val innboks = TestVarsel(type = Innboks, ident = ident).dbVarsel()
        val annenBeskjed = TestVarsel(type = Beskjed, ident = annenIdent).dbVarsel()
        val annenOppgave = TestVarsel(type = Oppgave, ident = annenIdent).dbVarsel()
        val annenInnboks = TestVarsel(type = Innboks, ident = annenIdent).dbVarsel()

        insertVarsel(beskjed, oppgave, innboks, annenBeskjed, annenOppgave, annenInnboks)

        val varsler = client.getVarsler("/varsel/sammendrag/alle")

        varsler.size shouldBe 3
        varsler.shouldFind { it.varselId == beskjed.varselId } shouldMatch beskjed
        varsler.shouldFind { it.varselId == oppgave.varselId } shouldMatch oppgave
        varsler.shouldFind { it.varselId == innboks.varselId } shouldMatch innboks
    }

    @Test
    fun `henter varsler av type`() = testVarselApi(userIdent = ident) { client ->
        val beskjed = TestVarsel(type = Beskjed, ident = ident).dbVarsel()
        val oppgave = TestVarsel(type = Oppgave, ident = ident).dbVarsel()
        val innboks = TestVarsel(type = Innboks, ident = ident).dbVarsel()

        insertVarsel(beskjed, oppgave, innboks)

        val beskjeder = client.getVarsler("/beskjed/sammendrag/alle")

        beskjeder.size shouldBe 1
        beskjeder.shouldFind { it.varselId == beskjed.varselId } shouldMatch beskjed

        val oppgaver = client.getVarsler("/oppgave/sammendrag/alle")

        oppgaver.size shouldBe 1
        oppgaver.shouldFind { it.varselId == oppgave.varselId } shouldMatch oppgave

        val innbokser = client.getVarsler("/innboks/sammendrag/alle")

        innbokser.size shouldBe 1
        innbokser.shouldFind { it.varselId == innboks.varselId } shouldMatch innboks
    }

    @Test
    fun `henter aktive og inaktive varsler`() = testVarselApi(userIdent = ident) { client ->
        val aktivBeskjed = TestVarsel(type = Beskjed, ident = ident, aktiv = true).dbVarsel()
        val inaktivBeskjed = TestVarsel(type = Beskjed, ident = ident, aktiv = false).dbVarsel()

        insertVarsel(aktivBeskjed, inaktivBeskjed)

        val alleBeskjeder = client.getVarsler("/beskjed/sammendrag/alle")

        alleBeskjeder.size shouldBe 2
        alleBeskjeder.shouldFind { it.varselId == aktivBeskjed.varselId } shouldMatch aktivBeskjed

        val aktiveBeskjeder = client.getVarsler("/beskjed/sammendrag/aktive")

        aktiveBeskjeder.size shouldBe 1
        aktiveBeskjeder.shouldFind { it.varselId == aktivBeskjed.varselId } shouldMatch aktivBeskjed

        val inaktiveBeskjeder = client.getVarsler("/beskjed/sammendrag/inaktive")

        inaktiveBeskjeder.size shouldBe 1
        inaktiveBeskjeder.shouldFind { it.varselId == inaktivBeskjed.varselId } shouldMatch inaktivBeskjed
    }

    @Test
    fun `maskerer innhold i varsel hvis bruker har for lav loa`() = testVarselApi(
        userIdent = ident,
        userLoa = LevelOfAssurance.SUBSTANTIAL
    ) { client ->
        val varsel = TestVarsel(type = Beskjed, ident = ident, sensitivitet = Substantial).dbVarsel()
        val sensitivtVarsel = TestVarsel(type = Beskjed, ident = ident, sensitivitet = High).dbVarsel()

        insertVarsel(varsel, sensitivtVarsel)

        val varsler = client.getVarsler("/varsel/sammendrag/alle")

        varsler.size shouldBe 2
        varsler.shouldFind { it.varselId == varsel.varselId } shouldMatch varsel

        varsler.shouldFind { it.varselId == sensitivtVarsel.varselId }.let {
            it.innhold shouldNotBe sensitivtVarsel.innhold
            it.innhold shouldBe null

            it.type shouldBe sensitivtVarsel.type
            it.varselId shouldBe sensitivtVarsel.varselId
            it.aktiv shouldBe sensitivtVarsel.aktiv
            it.eksternVarslingSendt shouldBe sensitivtVarsel.eksternVarslingStatus!!.sendt
            it.eksternVarslingKanaler shouldBe sensitivtVarsel.eksternVarslingStatus.kanaler
            it.opprettet shouldBe sensitivtVarsel.opprettet
            it.aktivFremTil shouldBe sensitivtVarsel.aktivFremTil
            it.inaktivert shouldBe sensitivtVarsel.inaktivert
        }
    }

    @Test
    fun `godttar varsler uten ekstern varsling`() = testVarselApi(userIdent = ident) { client ->
        val beskjed = TestVarsel(type = Beskjed, ident = ident).dbVarsel(withEksternVarsling = false)

        insertVarsel(beskjed)

        val varsler = client.getVarsler("/varsel/sammendrag/alle")

        varsler.size shouldBe 1

        varsler.shouldFind { it.varselId == beskjed.varselId }.let {
            it.innhold shouldMatch beskjed.innhold
            it.type shouldBe beskjed.type
            it.varselId shouldBe beskjed.varselId
            it.aktiv shouldBe beskjed.aktiv
            it.eksternVarslingSendt shouldBe false
            it.eksternVarslingKanaler shouldBe emptyList()
            it.opprettet shouldBe beskjed.opprettet
            it.aktivFremTil shouldBe beskjed.aktivFremTil
            it.inaktivert shouldBe beskjed.inaktivert
        }
    }

    @Test
    fun `henter varsler baser på query params`() = testVarselApi(userIdent = ident) { client ->
        val beskjed = TestVarsel(type = Beskjed, ident = ident).dbVarsel()
        val innboks = TestVarsel(type = Innboks, ident = ident, aktiv = false).dbVarsel()
        val oppgave1 = TestVarsel(type = Oppgave, ident = ident).dbVarsel()
        val oppgave2 = TestVarsel(type = Oppgave, ident = ident, aktiv = false).dbVarsel()

        insertVarsel(beskjed, innboks, oppgave1, oppgave2)

        client.getVarsler("/varsel/sammendrag").let { alleVarsler ->
            alleVarsler.size shouldBe 4
            alleVarsler.map { it.varselId }
                .shouldContainAll(beskjed.varselId, innboks.varselId, oppgave1.varselId, oppgave2.varselId)
        }

        client.getVarsler("/varsel/sammendrag?aktiv=true").let { aktiveVarsler ->
            aktiveVarsler.size shouldBe 2
            aktiveVarsler.map { it.varselId }
                .shouldContainAll(beskjed.varselId, oppgave1.varselId)
        }

        client.getVarsler("/varsel/sammendrag?type=oppgave&aktiv=false").let { inaktivOppave ->
            inaktivOppave.size shouldBe 1
            inaktivOppave.map { it.varselId }
                .shouldContainAll(oppgave2.varselId)
        }
    }

    @Test
    fun `henter tekst med preferert språk`() = testVarselApi(userIdent = ident) { client ->
        val dbBeskjed = TestVarsel(
            type = Beskjed,
            ident = ident,
            innhold = testInnhold(
                tekster = listOf(
                    Tekst("nb", "Norsk tekst", true),
                    Tekst("en", "Engelsk tekst", false),
                    Tekst("es", "Spansk tekst", false),
                )
            )
        ).dbVarsel()

        insertVarsel(dbBeskjed)

        client.getVarsler("/beskjed/sammendrag/alle?preferert_spraak=en").first().let { beskjed ->
            beskjed.innhold?.tekst shouldBe "Engelsk tekst"
            beskjed.innhold?.spraakkode shouldBe "en"
        }

        client.getVarsler("/beskjed/sammendrag/alle?preferert_spraak=nb").first().let { beskjed ->
            beskjed.innhold?.tekst shouldBe "Norsk tekst"
            beskjed.innhold?.spraakkode shouldBe "nb"
        }

        client.getVarsler("/varsel/sammendrag?type=beskjed&aktiv=true&preferert_spraak=es").first().let { beskjed ->
            beskjed.innhold?.tekst shouldBe "Spansk tekst"
            beskjed.innhold?.spraakkode shouldBe "es"
        }
    }

    @Test
    fun `henter tekst med default språk hvis ønsket språk ikke finnes`() = testVarselApi(userIdent = ident) { client ->
        val dbOppgave = TestVarsel(
            type = Oppgave,
            ident = ident,
            innhold = testInnhold(
                tekster = listOf(
                    Tekst("en", "Engelsk tekst", true),
                    Tekst("es", "Spansk tekst", false),
                )
            )
        ).dbVarsel()

        insertVarsel(dbOppgave)

        client.getVarsler("/oppgave/sammendrag/aktive?preferert_spraak=se").first().let { oppgave ->
            oppgave.innhold?.tekst shouldBe "Engelsk tekst"
            oppgave.innhold?.spraakkode shouldBe "en"
        }
    }

    @Test
    fun `bruker nb som default språkkode for eldre varsler`() = testVarselApi(userIdent = ident) { client ->
        val dbInnboks = TestVarsel(
            type = Innboks,
            aktiv = false,
            ident = ident,
            innhold = testInnhold(
                tekst = "Norsk tekst uten språkkode",
                tekster = emptyList()
            )
        ).dbVarsel()

        insertVarsel(dbInnboks)

        client.getVarsler("/innboks/sammendrag/alle?preferert_spraak=se").first().let { beskjed ->
            beskjed.innhold?.tekst shouldBe "Norsk tekst uten språkkode"
            beskjed.innhold?.spraakkode shouldBe "nb"
        }
    }


    private suspend fun HttpClient.getVarsler(path: String): List<Varselsammendrag> = get(path).body()


    private fun insertVarsel(vararg varsler: DatabaseVarsel) {
        varsler.forEach {
            writeRepository.insertVarsel(it)
        }
    }

    @KtorDsl
    private fun testVarselApi(
        userIdent: String = ident,
        userLoa: LevelOfAssurance = LevelOfAssurance.HIGH,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
    ) = baseTestApplication(
        userIdent = userIdent,
        userLoa = userLoa,
        readVarselRepository = readRepository,
        varselInaktiverer = varselInaktiverer,
        block = block,
    )
}
