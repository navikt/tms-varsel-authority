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
import io.mockk.mockk
import no.nav.tms.common.kubernetes.PodLeaderElection
import no.nav.tms.token.support.entraid.token.verification.mock.entraIdMock
import no.nav.tms.token.support.user.token.verificaton.mock.userTokenMock
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.SYSTEM_API
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.TestVarsel
import no.nav.tms.varsel.authority.mockProducer
import no.nav.tms.varsel.authority.read.ReadVarselRepository
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.RecordQueueRepository
import no.nav.tms.varsel.authority.write.RetryingKafkaProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.util.*

class InaktiverVarselApiTest {
    private val database = LocalPostgresDatabase.cleanDb()

    private val leaderElection: PodLeaderElection = mockk()

    private val mockProducer = mockProducer()
    private val retryingKafkaProducer = RetryingKafkaProducer(RecordQueueRepository(database), mockProducer, leaderElection)

    private val inaktivertProducer = VarselInaktivertProducer(retryingKafkaProducer, "topic")

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

    private fun testVarselApi(
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
    ) = testApplication {

        application {
            varselApi(
                readRepository,
                varselInaktiverer,
                installAuthenticatorsFunction = {
                    authentication {
                        userTokenMock {

                        }
                        entraIdMock(SYSTEM_API) {
                            enableDefaultAuthentication()
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
