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
import io.ktor.utils.io.*
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.TestVarsel
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.util.*

class InaktiverVarselApiTest {
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

    private val grunnForInaktivering = "Inaktiveres i test"


    @AfterEach
    fun deleteData() {
        LocalPostgresDatabase.cleanDb()
    }

    @Test
    fun `inaktiverer varsel for admin`() = testVarselApi {client ->
        val oppgave1 = TestVarsel(type = Varseltype.Oppgave, aktiv = true).dbVarsel()
        val oppgave2 = TestVarsel(type = Varseltype.Oppgave, aktiv = true).dbVarsel()

        insertVarsel(oppgave1, oppgave2)

        client.inaktiverVarsel(oppgave1.varselId, grunnForInaktivering)

        getDbVarsel(oppgave1.varselId).aktiv shouldBe false
        getDbVarsel(oppgave2.varselId).aktiv shouldBe true
    }

    @Test
    fun `svarer med feilkode hvis varsel ikke finnes`() = testVarselApi { client ->
        val beskjed = TestVarsel(type = Varseltype.Beskjed, aktiv = true).dbVarsel()

        insertVarsel(beskjed)

        val response = client.inaktiverVarsel(varselId = UUID.randomUUID().toString(), grunnForInaktivering)

        response.status shouldBe HttpStatusCode.Forbidden
    }

    private suspend fun HttpClient.inaktiverVarsel(varselId: String, grunn: String) =
        post("/varsel/inaktiver") {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(InaktiverVarselRequest(varselId, grunn))
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
                varselInaktiverer,
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

    private fun getDbVarsel(varselId: String): DatabaseVarsel = writeRepository.getVarsel(varselId)!!
}
