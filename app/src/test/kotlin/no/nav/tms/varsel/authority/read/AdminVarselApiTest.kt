package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.auth.authentication
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.action.Varseltype.Innboks
import no.nav.tms.varsel.action.Varseltype.Oppgave
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.EksternFeilHistorikkEntry
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.database.ArkiverteDbVarsel
import no.nav.tms.varsel.authority.database.ArkiverteDbVarsel.Companion.toZonedDateTimeUtc
import no.nav.tms.varsel.authority.database.ArkiverteDbVarsel.ConfidentlityLevel.LEVEL4
import no.nav.tms.varsel.authority.database.ArkiverteDbVarsel.ConfidentlityLevel.SUBSTANTIAL
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.dbVarsel
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.collections.forEach
import kotlin.collections.toList

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminVarselApiTest {
    private val ident = "123"
    private val database = LocalPostgresDatabase.cleanDb()
    private val readRepository = ReadVarselRepository(database)
    private val writeRepository = WriteVarselRepository(database)
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule()).registerModule(JavaTimeModule())

    private val endpoint = "/varsel/detaljert/alle/admin"
    val aktivtVarselOct2025 =
        dbVarsel(
            type = Beskjed,
            opprettet = "12-10-2025".toOsloZonedDateTime(),
            ident = ident,
            varselId = "aktivOct2025"
        )
    val inaktivtVarselOct2025 =
        dbVarsel(
            type = Beskjed,
            opprettet = "10-10-2025".toOsloZonedDateTime(),
            inaktivert = "10-10-2025".toOsloZonedDateTime(),
            ident = ident,
            aktiv = false,
            varselId = "inaktivOct2025"
        )
    val aktivtVarselJun2025 =
        dbVarsel(
            type = Oppgave,
            ident = ident,
            opprettet = "23-06-2025".toOsloZonedDateTime(),
            varselId = "aktivJun2025"
        )
    val varselDec2024Inaktivert2025 =
        dbVarsel(
            type = Oppgave,
            ident = ident,
            opprettet = "08-12-2024".toOsloZonedDateTime(),
            inaktivert = "01-01-2025".toOsloZonedDateTime(),
            aktiv = false,
            varselId = "varselDec2024Inaktivert2025"
        )
    val inaktivtVarselMay2023 =
        dbVarsel(
            type = Innboks,
            ident = ident,
            opprettet = "10-10-2023".toOsloZonedDateTime(),
            aktiv = false,
            varselId = "inaktivMay2023"
        )

    @BeforeEach
    fun addVarslerToDb() {
        insertVarsel(
            aktivtVarselJun2025,
            inaktivtVarselMay2023,
            aktivtVarselOct2025,
            inaktivtVarselOct2025,
            varselDec2024Inaktivert2025
        )
    }

    @AfterEach
    fun deleteData() {
        LocalPostgresDatabase.cleanDb()
    }

    @Test
    fun `Serialiserer varsel på alle format`() = testVarselApi {
        val testIdent = "789"
        val varsel = dbVarsel(
            opprettet = "23-06-2025".toOsloZonedDateTime().plusHours(11),
            varselId = "varsel1234",
            ident = testIdent,
            aktiv = false,
            inaktivert = "30-06-2025".toOsloZonedDateTime().plusHours(15),
            produsent = DatabaseProdusent(
                cluster = "test-cluster",
                namespace = "test-namespace",
                appnavn = "test-appnavn"
            ),
            eksternVarslingStatus = aktivtVarselJun2025.eksternVarslingStatus?.copy(
                renotifikasjonSendt = true,
                sistOppdatert = "26-06-2025".toOsloZonedDateTime().plusHours(10).plusMinutes(5),
                sisteStatus = EksternStatus.Ferdigstilt,
                feilhistorikk = listOf(
                    EksternFeilHistorikkEntry(
                        feilmelding = "Andre feilmelding",
                        tidspunkt = "25-06-2025".toOsloZonedDateTime().plusHours(8).plusMinutes(54),
                    ),
                    EksternFeilHistorikkEntry(
                        feilmelding = "Første feilmelding",
                        tidspunkt = "25-06-2025".toOsloZonedDateTime().plusHours(8).plusMinutes(53),
                    ),
                )
            )
        )
        val arkivertVarsel = ArkiverteDbVarsel.generateFromDatabaseVarsel(varsel, "arkivertVarsel65")
            .withCurrentProperties(
                nameSpace = varsel.produsent.namespace,
                renotifikasjonSendt = varsel.eksternVarslingStatus!!.renotifikasjonSendt,
                sistOppdatert = varsel.eksternVarslingStatus.sistOppdatert,
                feilhistorikk = varsel.eksternVarslingStatus.feilhistorikk.map {
                    ArkiverteDbVarsel.FeilhistorikkEntry(
                        feilmelding = it.feilmelding,
                        tidspunkt = it.tidspunkt
                    )
                },
                sisteStatus = varsel.eksternVarslingStatus.sisteStatus?.name?.lowercase()
            )
        val arkivertLegacyVarsel =
            ArkiverteDbVarsel.generateFromDatabaseVarsel(varsel, "arkivertLegacyVarsel65")
                .withLegacyProperties(
                    forstBehandlet = varsel.opprettet,
                    deaktivertPgaUtløptFrist = true,
                )
        database.insertCurrentArkiverteVarsler(testIdent, arkivertVarsel)
        database.insertLegacyArkiverteVarsler(testIdent, arkivertLegacyVarsel)
        insertVarsel(varsel)

        val varsler2025Response =
            client.getVarslerAsJson("$endpoint?fom=2025-01-01&tom=2025-12-31", testIdent)
        varsler2025Response["feilendeVarsler"].toList() shouldHaveSize 0

        varsler2025Response["varsler"].toList().apply {
            size shouldBe 3
            eachShouldBe("type", varsel.type.name.lowercase())
            eachShouldBe("aktiv", false, JsonNode::asBoolean)
            eachShouldBe("innhold.tekst", varsel.innhold.tekst)
            eachShouldBe("innhold.link", varsel.innhold.link)
            eachShouldBe("eksternVarsling.sendt", varsel.eksternVarslingStatus.sendt, JsonNode::asBoolean)
            eachShouldBe(
                "eksternVarsling.kanaler",
                varsel.eksternVarslingStatus.kanaler
            ) { toList().map { it.asText() } }

            val legacyVarsel = find { it["varselId"].asText() == arkivertLegacyVarsel.id }
            requireNotNull(legacyVarsel)


            filter { it["varselId"].asText() != arkivertLegacyVarsel.id }.apply {
                require(this.size == 2) { "Feil i filtrering av varsel, skal være 2 men er ${this.size}" }
                eachShouldBe("produsertAv", "test-appnavn(test-namespace)", JsonNode::asText)
                eachShouldBe(
                    "tilgangstyring",
                    "Idporten level of assurance ${varsel.sensitivitet.name.lowercase()}",
                )
                eachShouldBe(
                    "eksternVarsling.tilleggsopplysninger[0]",
                    "Siste oppdatering: 26.06.2025 kl 10:05 (UTC+02:00)",
                )
                eachShouldBe(
                    "eksternVarsling.tilleggsopplysninger[1]",
                    "Sendt som batch",
                )
                eachShouldBe(
                    "eksternVarsling.tilleggsopplysninger[2]",
                    "Re-notifikasjon sendt",
                )
                eachShouldBe(
                    "eksternVarsling.tilleggsopplysninger[3]",
                    """2 oppføringer i feilhistorikk:
                                |25.06.2025 kl 08:53 (UTC+02:00): Første feilmelding
                                |25.06.2025 kl 08:54 (UTC+02:00): Andre feilmelding
                                |----------""".trimMargin(),
                )
                eachShouldBe(
                    "eksternVarsling.tilleggsopplysninger[4]",
                    "Siste status: ferdigstilt"
                )
            }
        }
    }

    @Test
    fun `henter alle varsel for bruker i tidsperiode`() = testVarselApi {

        val varsler2025 =
            client.getVarslerAsJson("$endpoint?fom=2025-01-01&tom=2025-12-31", ident)["varsler"].toList()

        varsler2025.varselIds shouldContainOnly listOf(
            aktivtVarselJun2025,
            aktivtVarselOct2025,
            inaktivtVarselOct2025,
            varselDec2024Inaktivert2025
        ).ids

        val varsler2023til20205 =
            client.getVarslerAsJson("$endpoint?fom=2023-01-01&tom=2025-12-31", ident)["varsler"].toList()
        varsler2023til20205.varselIds shouldContainOnly listOf(
            aktivtVarselJun2025,
            inaktivtVarselMay2023,
            aktivtVarselOct2025,
            inaktivtVarselOct2025,
            varselDec2024Inaktivert2025
        ).ids

        val varslerBefore2025 =
            client.getVarslerAsJson("${endpoint}?fom=2023-01-01&tom=2024-12-31", ident)["varsler"].toList()
        varslerBefore2025.varselIds shouldContainOnly listOf(
            varselDec2024Inaktivert2025,
            inaktivtVarselMay2023
        ).ids

        val varselOpprettetFørMenAktivEtter =
            client.getVarslerAsJson("${endpoint}?fom=2025-01-01&tom=2025-06-22", ident)["varsler"].toList()
        varselOpprettetFørMenAktivEtter.varselIds shouldContainOnly listOf(varselDec2024Inaktivert2025).ids
    }

    @Test
    fun `inkluderer arkiverte varsler for bruker i tidsperiode`() = testVarselApi {
        val varselAug2020 = ArkiverteDbVarsel(
            id = "legacyAug2020",
            ident = ident,
            confidentilality = LEVEL4,
        ).withLegacyProperties(forstBehandletStr = "2020-08-10")
        val legacyJsonFeb2023 =
            varselAug2020.copy(id = "legacyFeb2023", forstBehandlet = "2023-02-18".toZonedDateTimeUtc())

        val varselJsonJan2025 = ArkiverteDbVarsel(
            id = "arkivertJan2025",
            ident = ident,
            confidentilality = SUBSTANTIAL
        ).withCurrentProperties()

        val uventetVarselformatId = "uventetVarselFormat"
        val uventetVarselFormat = """
                    {
                    "link": "https://arbeidsplassen-q.nav.no/cv",
                      "type": "oppgave",
                      "aktiv": false,
                      "tekst": "Du må oppdatere CV-en og jobbprofilen på arbeidsplassen.no",
                      "forstBehandlet": "2020-08-03T11:13:55.917Z",
                      "eventId": "$uventetVarselformatId"
                    }
                """.trimIndent()

        database.insertLegacyArkiverteVarsler(ident, legacyJsonFeb2023, varselAug2020)
        database.insertCurrentArkiverteVarsler(ident, varselJsonJan2025)
        database.insertArkivertVarsel(
            ident = ident,
            varselId = uventetVarselformatId,
            jsonBlob = uventetVarselFormat
        )

        val varsler2020til20205 =
            client.getVarslerAsJson("$endpoint?fom=2020-01-01&tom=2025-12-31", ident)
        varsler2020til20205["varsler"].toList().size shouldBe 8
        varsler2020til20205["feilendeVarsler"].toList().map {
            it.asText()
        } shouldContainOnly listOf(uventetVarselformatId)

        val varsler2023til2025 =
            client.getVarslerAsJson("$endpoint?fom=2023-02-01&tom=2025-12-31", ident)["varsler"].toList()
        varsler2023til2025.size shouldBe 7

        val varslerMayToDec2025 =
            client.getVarslerAsJson("$endpoint?fom=2025-05-01&tom=2025-12-31", ident)["varsler"].toList()
        varslerMayToDec2025.size shouldBe 3
    }

    private fun testVarselApi(
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
    ) = testApplication {

        application {
            varselApi(
                readRepository,
                mockk(),
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

    private fun String.toOsloZonedDateTime() =
        LocalDate.parse(this, DateTimeFormatter.ofPattern("dd-MM-yyyy")).atStartOfDay().atZone(ZoneId.of("Europe/Oslo"))

    private val List<DatabaseVarsel>.ids
        get() = map { it.varselId }

    private val List<JsonNode>.varselIds
        get() = map { it["varselId"].asText() }

    private suspend fun HttpClient.getVarslerAsJson(path: String, ident: String): JsonNode = get(path) {
        headers.append("ident", ident)
    }.let {
        it.status shouldBe HttpStatusCode.OK
        objectMapper.readTree(it.bodyAsText())
    }

    private fun insertVarsel(vararg varsler: DatabaseVarsel) {
        varsler.forEach {
            writeRepository.insertVarsel(it)
        }
    }
}


fun LocalPostgresDatabase.insertLegacyArkiverteVarsler(ident: String, vararg varsler: ArkiverteDbVarsel) {
    varsler.forEach {
        insertArkivertVarsel(ident, it.id, it.legacyJsonFormat())
    }
}

fun LocalPostgresDatabase.insertCurrentArkiverteVarsler(ident: String, vararg varsler: ArkiverteDbVarsel) {
    varsler.forEach {
        insertArkivertVarsel(ident, it.id, it.currentJsonFormat())
    }
}

private fun List<JsonNode>.eachShouldBe(
    fieldName: String,
    expectedValue: String?,
) {
    eachShouldBe(fieldName, expectedValue, JsonNode::asText)
}


private fun <T> List<JsonNode>.eachShouldBe(
    path: String,
    expectedValue: T,
    typeCast: JsonNode.() -> T
) {
    forEach {
        val keys = path.split(".", "[", "]").filter { fieldname -> fieldname.isNotEmpty() }.toMutableList()
        withClue("$path in varsel with id ${it["varselId"].asText()} does not contain the expected value") {
            var node = it.path(keys.removeFirst())
            while (keys.isNotEmpty()) {
                val key = keys.removeFirst()
                node = key.toIntOrNull()?.let { index ->
                    require(node.isArray)
                    require(node.size() > index) {
                        """$path in varsel with id ${it["varselId"].asText()} does not have enough elements in array, 
                            |expected value $expectedValue
                            |actual array was of size ${node.toList().size} with content content $node
                            """.trimIndent()
                    }
                    node[index]
                }
                    ?: node[key]
            }
            require(!node.isNull && !node.isMissingNode) { "$path in varsel with id ${it["varselId"].asText()} does not exist" }
            node.typeCast() shouldBe expectedValue
        }
    }
}