package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.util.*
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.dbVarsel
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DateFormat

class SaksbehandlerVarselApiTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

    private val inaktivertProducer = VarselInaktivertProducer(mockProducer, "topic")

    private val readRepository = ReadVarselRepository(database)
    private val writeRepository = WriteVarselRepository(database)
    private val beskjedInaktiverer = VarselInaktiverer(writeRepository, inaktivertProducer)

    private val ident = "123"

    @AfterEach
    fun deleteData() {
        LocalPostgresDatabase.cleanDb()
    }

    @Test
    fun `henter varsler for bruker`() = testVarselApi{  client ->
        val annenIdent = "456"

        val beskjed = dbVarsel(type = Beskjed, ident = ident)
        val oppgave = dbVarsel(type = Oppgave, ident = ident)
        val innboks = dbVarsel(type = Innboks, ident = ident)
        val annenBeskjed = dbVarsel(type = Beskjed, ident = annenIdent)
        val annenOppgave = dbVarsel(type = Oppgave, ident = annenIdent)
        val annenInnboks = dbVarsel(type = Innboks, ident = annenIdent)

        insertVarsel(beskjed, oppgave, innboks, annenBeskjed, annenOppgave, annenInnboks)

        val varsler = client.getVarsler("/varsel/detaljert/alle", ident)

        varsler.size shouldBe 3
        varsler.shouldFind { it.varselId == beskjed.varselId } shouldMatch beskjed
        varsler.shouldFind { it.varselId == oppgave.varselId } shouldMatch oppgave
        varsler.shouldFind { it.varselId == innboks.varselId } shouldMatch innboks
    }

    @Test
    fun `henter varsler av type`() = testVarselApi { client ->
        val beskjed = dbVarsel(type = Beskjed, ident = ident)
        val oppgave = dbVarsel(type = Oppgave, ident = ident)
        val innboks = dbVarsel(type = Innboks, ident = ident)

        insertVarsel(beskjed, oppgave, innboks)

        val beskjeder = client.getVarsler("/beskjed/detaljert/alle", ident)

        beskjeder.size shouldBe 1
        beskjeder.shouldFind { it.varselId == beskjed.varselId } shouldMatch beskjed

        val oppgaver = client.getVarsler("/oppgave/detaljert/alle", ident)

        oppgaver.size shouldBe 1
        oppgaver.shouldFind { it.varselId == oppgave.varselId } shouldMatch oppgave

        val innbokser = client.getVarsler("/innboks/detaljert/alle", ident)

        innbokser.size shouldBe 1
        innbokser.shouldFind { it.varselId == innboks.varselId } shouldMatch innboks
    }

    @Test
    fun `henter aktive og inaktive varsler`() = testVarselApi { client ->
        val aktivBeskjed = dbVarsel(type = Beskjed, ident = ident, aktiv = true)
        val inaktivBeskjed = dbVarsel(type = Beskjed, ident = ident, aktiv = false)

        insertVarsel(aktivBeskjed, inaktivBeskjed)

        val alleBeskjeder = client.getVarsler("/beskjed/detaljert/alle", ident)

        alleBeskjeder.size shouldBe 2
        alleBeskjeder.shouldFind { it.varselId == aktivBeskjed.varselId } shouldMatch aktivBeskjed

        val aktiveBeskjeder = client.getVarsler("/beskjed/detaljert/aktive", ident)

        aktiveBeskjeder.size shouldBe 1
        aktiveBeskjeder.shouldFind { it.varselId == aktivBeskjed.varselId } shouldMatch aktivBeskjed

        val inaktiveBeskjeder = client.getVarsler("/beskjed/detaljert/inaktive", ident)

        inaktiveBeskjeder.size shouldBe 1
        inaktiveBeskjeder.shouldFind { it.varselId == inaktivBeskjed.varselId } shouldMatch inaktivBeskjed
    }

    @Test
    fun `godtar varsler der ekstern varsling er null`() = testVarselApi{  client ->

        val beskjed = dbVarsel(type = Beskjed, ident = ident, eksternVarslingStatus = null, eksternVarslingBestilling = null)

        insertVarsel(beskjed)

        val varsler = client.getVarsler("/varsel/detaljert/alle", ident)

        varsler.size shouldBe 1
        varsler.shouldFind { it.varselId == beskjed.varselId }.let {
            it.type shouldBe beskjed.type
            it.varselId shouldBe beskjed.varselId
            it.aktiv shouldBe beskjed.aktiv
            it.produsent shouldBe beskjed.produsent
            it.sensitivitet shouldBe beskjed.sensitivitet
            it.innhold shouldBe beskjed.innhold
            it.eksternVarsling shouldBe null
            it.opprettet shouldBe beskjed.opprettet
            it.aktivFremTil shouldBe beskjed.aktivFremTil
            it.inaktivert shouldBe beskjed.inaktivert
            it.inaktivertAv shouldBe beskjed.inaktivertAv
        }
    }

    private suspend fun HttpClient.getVarsler(path: String, ident: String): List<DetaljertVarsel> = get(path){
        headers.append("ident", ident)
    }.body()

    private fun List<DetaljertVarsel>.shouldFind(predicate: (DetaljertVarsel) -> Boolean): DetaljertVarsel {
        val varsel = find(predicate)

        varsel.shouldNotBeNull()

        return varsel
    }

    private infix fun DetaljertVarsel.shouldMatch(dbVarsel: DatabaseVarsel) {
        type shouldBe dbVarsel.type
        varselId shouldBe dbVarsel.varselId
        aktiv shouldBe dbVarsel.aktiv
        produsent shouldBe dbVarsel.produsent
        sensitivitet shouldBe dbVarsel.sensitivitet
        innhold shouldBe dbVarsel.innhold
        eksternVarsling shouldBe dbVarsel.eksternVarslingStatus
        opprettet shouldBe dbVarsel.opprettet
        aktivFremTil shouldBe dbVarsel.aktivFremTil
        inaktivert shouldBe dbVarsel.inaktivert
        inaktivertAv shouldBe dbVarsel.inaktivertAv
    }

    private fun insertVarsel(vararg varsler: DatabaseVarsel) {
        varsler.forEach {
            writeRepository.insertVarsel(it)
        }
    }

    @KtorDsl
    private fun testVarselApi(
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
                        }
                        azureMock {
                            setAsDefault = false
                            alwaysAuthenticated = true
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

