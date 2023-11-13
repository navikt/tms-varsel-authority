package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.util.*
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.LevelOfAssurance
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.action.Sensitivitet.High
import no.nav.tms.varsel.action.Sensitivitet.Substantial
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.database.dbVarsel
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.aktiver.WriteVarselRepository
import no.nav.tms.varsel.authority.write.inaktiver.BeskjedInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DateFormat

class BrukerVarselApiTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val inaktivertProducer = VarselInaktivertProducer(mockProducer, "topic")

    private val readRepository = ReadVarselRepository(database)
    private val writeRepository = WriteVarselRepository(database)
    private val beskjedInaktiverer = BeskjedInaktiverer(writeRepository, inaktivertProducer)

    private val ident = "123"

    @AfterEach
    fun deleteData() {
        LocalPostgresDatabase.cleanDb()
    }

    @Test
    fun `henter varsler for bruker`() = testVarselApi(userIdent = ident){  client ->
        val annenIdent = "456"

        val beskjed = dbVarsel(type = Beskjed, ident = ident, innhold = Innhold("Tekst uten lenke", null))
        val oppgave = dbVarsel(type = Oppgave, ident = ident)
        val innboks = dbVarsel(type = Innboks, ident = ident)
        val annenBeskjed = dbVarsel(type = Beskjed, ident = annenIdent)
        val annenOppgave = dbVarsel(type = Oppgave, ident = annenIdent)
        val annenInnboks = dbVarsel(type = Innboks, ident = annenIdent)

        insertVarsel(beskjed, oppgave, innboks, annenBeskjed, annenOppgave, annenInnboks)

        val varsler = client.getVarsler("/varsel/sammendrag/alle")

        varsler.size shouldBe 3
        varsler.shouldFind { it.varselId == beskjed.varselId } shouldMatch beskjed
        varsler.shouldFind { it.varselId == oppgave.varselId } shouldMatch oppgave
        varsler.shouldFind { it.varselId == innboks.varselId } shouldMatch innboks
    }

    @Test
    fun `henter varsler av type`() = testVarselApi(userIdent = ident) { client ->
        val beskjed = dbVarsel(type = Beskjed, ident = ident)
        val oppgave = dbVarsel(type = Oppgave, ident = ident)
        val innboks = dbVarsel(type = Innboks, ident = ident)

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
        val aktivBeskjed = dbVarsel(type = Beskjed, ident = ident, aktiv = true)
        val inaktivBeskjed = dbVarsel(type = Beskjed, ident = ident, aktiv = false)

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
        val varsel = dbVarsel(type = Beskjed, ident = ident, sensitivitet = Substantial)
        val sensitivtVarsel = dbVarsel(type = Beskjed, ident = ident, sensitivitet = High)

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
            it.eksternVarslingKanaler shouldBe sensitivtVarsel.eksternVarslingStatus!!.kanaler
            it.opprettet shouldBe sensitivtVarsel.opprettet
            it.aktivFremTil shouldBe sensitivtVarsel.aktivFremTil
            it.inaktivert shouldBe sensitivtVarsel.inaktivert
        }
    }

    @Test
    fun `godttar varsler uten ekstern varsling`() = testVarselApi(userIdent = ident) { client ->
        val beskjed = dbVarsel(type = Beskjed, ident = ident, eksternVarslingBestilling = null, eksternVarslingStatus = null)

        insertVarsel(beskjed)

        val varsler = client.getVarsler("/varsel/sammendrag/alle")

        varsler.size shouldBe 1

        varsler.shouldFind { it.varselId == beskjed.varselId }.let {
            it.innhold shouldBe beskjed.innhold
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

    private suspend fun HttpClient.getVarsler(path: String): List<DatabaseVarselsammendrag> = get(path).body()

    private fun List<DatabaseVarselsammendrag>.shouldFind(predicate: (DatabaseVarselsammendrag) -> Boolean): DatabaseVarselsammendrag {
        val varsel = find(predicate)

        varsel.shouldNotBeNull()

        return varsel
    }

    private infix fun DatabaseVarselsammendrag.shouldMatch(dbVarsel: DatabaseVarsel) {
        type shouldBe dbVarsel.type
        varselId shouldBe dbVarsel.varselId
        aktiv shouldBe dbVarsel.aktiv
        innhold shouldBe dbVarsel.innhold
        eksternVarslingSendt shouldBe dbVarsel.eksternVarslingStatus!!.sendt
        eksternVarslingKanaler shouldBe dbVarsel.eksternVarslingStatus!!.kanaler
        opprettet shouldBe dbVarsel.opprettet
        aktivFremTil shouldBe dbVarsel.aktivFremTil
        inaktivert shouldBe dbVarsel.inaktivert
    }

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
    ) = testApplication {

        application {
            varselApi(
                readRepository,
                beskjedInaktiverer,
                installAuthenticatorsFunction = {
                    authentication {
                        tokenXMock {
                            setAsDefault = true
                            alwaysAuthenticated = true
                            staticUserPid = userIdent
                            staticLevelOfAssurance = userLoa
                        }
                        azureMock {
                            setAsDefault = false
                        }
                    }
                }
            )
        }

        this.block(
            client.config {
                install(ContentNegotiation) {
                    jackson {
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        registerModule(JavaTimeModule())
                        dateFormat = DateFormat.getDateTimeInstance()
                    }
                }
            }
        )
    }
}

