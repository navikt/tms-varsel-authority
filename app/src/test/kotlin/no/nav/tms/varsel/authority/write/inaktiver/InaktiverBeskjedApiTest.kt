package no.nav.tms.varsel.authority.write.inaktiver

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.util.*
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.LevelOfAssurance.HIGH
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.dbVarsel
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.util.*

class InaktiverBeskjedApiTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val mockProducer = MockProducer(
        false,
        StringSerializer(),
        StringSerializer()
    )

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
    fun `inaktiverer varsel for bruker`() = testVarselApi(userIdent = ident) {client ->
        val beskjed1 = dbVarsel(type = Varseltype.Beskjed, ident = ident, aktiv = true)
        val beskjed2 = dbVarsel(type = Varseltype.Beskjed, ident = ident, aktiv = true)

        insertVarsel(beskjed1, beskjed2)

        client.inaktiverBeskjed(beskjed1.varselId)

        getDbVarsel(beskjed1.varselId).aktiv shouldBe false
        getDbVarsel(beskjed2.varselId).aktiv shouldBe true
    }

    @Test
    fun `svarer med feilkode hvis varsel ikke finnes`() = testVarselApi(userIdent = ident) { client ->
        val beskjed = dbVarsel(type = Varseltype.Beskjed, ident = ident, aktiv = true)

        insertVarsel(beskjed)

        val response = client.inaktiverBeskjed(varselId = UUID.randomUUID().toString())

        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `svarer med feilkode hvis varsel ikke er beskjed`() = testVarselApi(userIdent = ident) { client ->
        val oppgave = dbVarsel(type = Varseltype.Oppgave, ident = ident, aktiv = true)

        insertVarsel(oppgave)

        val response = client.inaktiverBeskjed(varselId = oppgave.varselId)

        response.status shouldBe HttpStatusCode.Forbidden
    }

    @Test
    fun `svarer med feilkode hvis varsel eies av annen bruker`() = testVarselApi(userIdent = ident) {client ->
        val beskjed = dbVarsel(type = Varseltype.Beskjed, ident = "annenIdent", aktiv = true)

        insertVarsel(beskjed)

        val response = client.inaktiverBeskjed(varselId = beskjed.varselId)

        response.status shouldBe HttpStatusCode.Forbidden
    }

    private suspend fun HttpClient.inaktiverBeskjed(varselId: String) =
        post("/beskjed/inaktiver") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(VarselIdBody(varselId))
        }

    private fun insertVarsel(vararg varsler: DatabaseVarsel) {
        varsler.forEach {
            writeRepository.insertVarsel(it)
        }
    }

    @KtorDsl
    private fun testVarselApi(
        userIdent: String,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
    ) = testApplication {

        application {
            varselApi(
                readRepository,
                varselInaktiverer,
                installAuthenticatorsFunction = {
                    authentication {
                        tokenXMock {
                            setAsDefault = true
                            alwaysAuthenticated = true
                            staticUserPid = userIdent
                            staticLevelOfAssurance = HIGH
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

    private fun getDbVarsel(varselId: String): DatabaseVarsel = writeRepository.getVarsel(varselId)!!
}
