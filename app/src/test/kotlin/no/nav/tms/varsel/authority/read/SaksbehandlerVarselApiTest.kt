package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.*
import io.ktor.server.auth.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.action.Varseltype.*
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.dbVarsel
import no.nav.tms.varsel.authority.database.legacyVarselJson
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktiverer
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertProducer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID


class SaksbehandlerVarselApiTest {
    private val database = LocalPostgresDatabase.cleanDb()
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()).registerModule(JavaTimeModule())

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

    @Nested
    inner class AlleVarsler {
        val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val zoneId = ZoneId.of("Europe/Oslo")


        @Test
        fun `henter varsler for bruker`() = testVarselApi { client ->
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

        @Nested
        inner class Admin {
            private val endpoint = "/varsel/detaljert/alle/admin"
            val aktivtVarselOct2025 =
                dbVarsel(type = Beskjed, opprettet = "12-10-2025".toZonedDateTime(), ident = ident)
            val inaktivtVarselOct2025 =
                dbVarsel(
                    type = Beskjed,
                    opprettet = "10-10-2025".toZonedDateTime(),
                    inaktivert = "10-10-2025".toZonedDateTime(),
                    ident = ident,
                    aktiv = false
                )
            val aktivtVarselJun2025 =
                dbVarsel(type = Oppgave, ident = ident, opprettet = "23-06-2025".toZonedDateTime())
            val inaktivtVarselJun2024 =
                dbVarsel(
                    type = Oppgave,
                    ident = ident,
                    opprettet = "08-06-2024".toZonedDateTime(),
                    inaktivert = "21-06-2025".toZonedDateTime(),
                    aktiv = false
                )
            val inaktivtVarselMay2023 =
                dbVarsel(type = Innboks, ident = ident, opprettet = "10-10-2023".toZonedDateTime(), aktiv = false)

            @BeforeEach
            fun addVarslerToDb() {
                insertVarsel(
                    aktivtVarselJun2025,
                    inaktivtVarselMay2023,
                    aktivtVarselOct2025,
                    inaktivtVarselOct2025,
                    inaktivtVarselJun2024
                )
            }

            @Test
            fun `henter alle varsel for bruker i tidsperiode`() = testVarselApi {

                val varsler2025 = client.getVarslerAsJson("$endpoint?fom=2025-01-01&tom=2025-12-31", ident)
                varsler2025.size shouldBe 4
                varsler2025.mapIds() shouldContainOnly listOf(
                    aktivtVarselJun2025,
                    aktivtVarselOct2025,
                    inaktivtVarselOct2025,
                    inaktivtVarselJun2024
                ).ids()

                val varsler2023til20205 =
                    client.getVarslerAsJson("$endpoint?fom=2023-01-01&tom=2025-12-31", ident)
                varsler2023til20205.size shouldBe 5
                varsler2023til20205.mapIds() shouldContainOnly listOf(
                    aktivtVarselJun2025,
                    inaktivtVarselMay2023,
                    aktivtVarselOct2025,
                    inaktivtVarselOct2025,
                    inaktivtVarselJun2024
                ).ids()

                val varslerBefore2025 =
                    client.getVarslerAsJson("${endpoint}?fom=2023-01-01&tom=2024-12-31", ident)
                varslerBefore2025.size shouldBe 2
                varslerBefore2025.mapIds() shouldContainOnly listOf(inaktivtVarselJun2024, inaktivtVarselMay2023).ids()

                val varselOpprettetFørMenAktivEtter =
                    client.getVarslerAsJson("${endpoint}?fom=2025-06-10&tom=2025-06-22", ident)
                varselOpprettetFørMenAktivEtter.size shouldBe 1
                varselOpprettetFørMenAktivEtter.mapIds() shouldContainOnly listOf(inaktivtVarselJun2024).ids()
            }


            @Test
            fun `inkluderer arkiverte varsler for bruker i tidsperiode`() = testVarselApi {
                val legacyJsonAug2020 = legacyVarselJson(id = "legacyAug2020", datoString = "2020-08-19")
                val legacyJsonFeb2023 = legacyVarselJson(id = "legacyFeb2023", datoString = "2023-02-18")
                val varselJsonJan2025 = """
                    {
                    "type": "innboks",
                    "aktiv": false,
                    "ident": "$ident",
                    "innhold": {
                    "link": "https://test.pest.no/skriv",
                    "tekst": "Du har mottatt en ny melding fra NAV som ligger i din innboks.",
                    "tekster": [
                    {
                        "tekst": "Du har mottatt en ny melding fra NAV som ligger i din innboks.",
                        "default": true,
                        "spraakkode": "nb"
                    }
                    ]
                },
                    "varselId": "arkivertJan2025",
                    "opprettet": "2025-01-04T10:56:18.325+01:00",
                    "produsent": {
                    "appnavn": "minside-testapp",
                    "cluster": "test-cluster",
                    "namespace": "team-test"
                },
                    "inaktivert": "2025-02-11T13:25:06.631+01:00",
                    "inaktivertAv": "produsent",
                    "sensitivitet": "high",
                    "eksternVarslingStatus": {
                    "sendt": false,
                    "kanaler": [],
                    "feilhistorikk": [
                    {
                        "tidspunkt": "2024-11-04T09:56:19.854Z",
                        "feilmelding": "mottaker mangler gyldig kontaktinformasjon i kontakt- og reservasjonsregisteret"
                    }
                    ],
                    "sendtSomBatch": false,
                    "sistOppdatert": "2024-11-04T09:56:19.944Z",
                    "renotifikasjonSendt": false
                },
                    "eksternVarslingBestilling": {
                    "kanBatches": false,
                    "prefererteKanaler": [
                    "SMS",
                    "EPOST"
                    ]
                }
                }
                """.trimIndent()
                database.insertArkivertVarsel(ident = ident, varselId = "legacyAug2020", jsonBlob = legacyJsonAug2020)
                database.insertArkivertVarsel(ident = ident, varselId = "legacyFeb2023", jsonBlob = legacyJsonFeb2023)
                database.insertArkivertVarsel(ident = ident, varselId = "arkivertJan2025", jsonBlob = varselJsonJan2025)

                val varsler2020til20205 =
                    client.getVarslerAsJson("$endpoint?fom=2020-01-01&tom=2025-12-31", ident)
                varsler2020til20205.size shouldBe 8

                val varsler2023til2025 =
                    client.getVarslerAsJson("$endpoint?fom=2023-02-01&tom=2025-12-31", ident)
                varsler2023til2025.size shouldBe 7

                val varslerMayToDec2025 = client.getVarslerAsJson("$endpoint?fom=2025-05-01&tom=2025-12-31", ident)
                varslerMayToDec2025.size shouldBe 3
            }

            private infix fun List<JsonNode>.shouldContainArkivertVarselWithId(varselId: String) {
                val varsel = this.firstOrNull { it["varselId"].asText() == varselId }
                withClue("Forventet varsel med id $varselId finnes ikke i responsen") {
                    varsel.shouldNotBeNull()
                }
                withClue("Forventet at varsel med id $varselId er ikke markert som arkivert") {
                    varsel!!["arkivert"].asBoolean() shouldBe true
                }
            }
        }

        private suspend fun HttpClient.getVarslerAsJson(path: String, ident: String): List<JsonNode> = get(path) {
            headers.append("ident", ident)
        }.let {
            it.status shouldBe HttpStatusCode.OK
            objectMapper.readTree(it.bodyAsText()).toList()
        }

        private fun String.toZonedDateTime() =
            LocalDate.parse(this, dateFormat).atStartOfDay().atZone(zoneId)

        private fun List<DatabaseVarsel>.ids(): List<String> = map { it.varselId }

        private fun List<JsonNode>.mapIds() = map { it["varselId"].asText() }
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
    fun `godtar varsler der ekstern varsling er null`() = testVarselApi { client ->

        val beskjed =
            dbVarsel(type = Beskjed, ident = ident, eksternVarslingStatus = null, eksternVarslingBestilling = null)

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

    private suspend fun HttpClient.getVarsler(path: String, ident: String): List<DetaljertVarsel> = get(path) {
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
}

