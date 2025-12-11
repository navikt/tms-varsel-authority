package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authentication
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.LevelOfAssurance
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Sensitivitet.Substantial
import no.nav.tms.varsel.action.Varseltype.Beskjed
import no.nav.tms.varsel.action.Varseltype.Innboks
import no.nav.tms.varsel.action.Varseltype.Oppgave
import no.nav.tms.varsel.authority.DatabaseProdusent
import no.nav.tms.varsel.authority.EksternFeilHistorikkEntry
import no.nav.tms.varsel.authority.EksternStatus
import no.nav.tms.varsel.authority.read.JsonHelpers.findElementsWithKey
import no.nav.tms.varsel.authority.read.JsonHelpers.shouldHaveValue
import no.nav.tms.varsel.authority.database.LocalPostgresDatabase
import no.nav.tms.varsel.authority.database.TestVarsel
import no.nav.tms.varsel.authority.database.TestVarsel.Companion.ids
import no.nav.tms.varsel.authority.database.TestVarsel.Companion.varselIds
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
        TestVarsel(
            type = Beskjed,
            opprettet = "12-10-2025".toOsloZonedDateTime(),
            ident = ident,
            varselId = "aktivOct2025"
        )
    val inaktivtVarselOct2025 =
        TestVarsel(
            type = Beskjed,
            opprettet = "10-10-2025".toOsloZonedDateTime(),
            inaktivert = "10-10-2025".toOsloZonedDateTime(),
            ident = ident,
            aktiv = false,
            varselId = "inaktivOct2025"
        )
    val aktivtVarselJun2025 =
        TestVarsel(
            type = Oppgave,
            ident = ident,
            opprettet = "23-06-2025".toOsloZonedDateTime(),
            varselId = "aktivJun2025"
        )
    val varselDec2024Inaktivert2025 =
        TestVarsel(
            type = Oppgave,
            ident = ident,
            opprettet = "08-12-2024".toOsloZonedDateTime(),
            inaktivert = "01-01-2025".toOsloZonedDateTime(),
            aktiv = false,
            varselId = "varselDec2024Inaktivert2025"
        )
    val inaktivtVarselMay2023 =
        TestVarsel(
            type = Innboks,
            ident = ident,
            opprettet = "10-10-2023".toOsloZonedDateTime(),
            aktiv = false,
            varselId = "inaktivMay2023"
        )

    @BeforeEach
    fun addVarslerToDb() {
        writeRepository.insertTestVarsel(
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
    fun `Serialiserer varsel på alle format`() = testVarselApi(userIdent = "789") {
        val testIdent = "789"
        val varselData = TestVarsel(
            opprettet = "23-06-2025".toOsloZonedDateTime().plusHours(11),
            varselId = "varsel1234",
            ident = testIdent,
            aktiv = false,
            inaktivert = "30-06-2025".toOsloZonedDateTime().plusHours(15),
            produsent = DatabaseProdusent(
                cluster = "test-cluster",
                namespace = "test-namespace",
                appnavn = "test-appnavn"
            )
        ).apply {
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
        }
        val arkivertVarsel = varselData.deepCopy(varselId = "arkivertVarsel")

        val arkivertLegacyVarsel = varselData.deepCopy(
            varselId = "arkivertLegacyVarsel65",
            aktiv = false
        ).withLegacyProperties(
            utløptFrist = true
        )
        database.insertCurrentArkiverteVarsler(testIdent, arkivertVarsel)
        database.insertLegacyArkiverteVarsler(testIdent, arkivertLegacyVarsel)
        writeRepository.insertTestVarsel(varselData)

        val varsler2025Response =
            client.getVarslerAsJson("$endpoint?fom=2025-01-01&tom=2025-12-31", testIdent)
        varsler2025Response["feilendeVarsler"].map { it.asText() } shouldBe emptyList()

        val alleFormatVarsel = varsler2025Response["varsler"].toList()
        val legacyFormatVarsel = alleFormatVarsel.find { it["varselId"].asText() == arkivertLegacyVarsel.varselId }!!
        val currentFormatVarsel = alleFormatVarsel.filter { it["varselId"].asText() != arkivertLegacyVarsel.varselId }

        alleFormatVarsel.size shouldBe 3

        alleFormatVarsel findElementsWithKey "type" shouldHaveValue varselData.type.name.lowercase()
        alleFormatVarsel findElementsWithKey "innhold.tekst" shouldHaveValue varselData.innhold.tekst
        alleFormatVarsel findElementsWithKey "innhold.link" shouldHaveValue varselData.innhold.link!!
        alleFormatVarsel findElementsWithKey "eksternVarsling.sendt" shouldHaveValue varselData.eksternVarslingStatus!!.sendt
        alleFormatVarsel findElementsWithKey "eksternVarsling.kanaler" shouldHaveValue varselData.eksternVarslingStatus!!.kanaler
        alleFormatVarsel findElementsWithKey "aktiv" shouldHaveValue varselData.aktiv

        legacyFormatVarsel["produsertAv"].asText() shouldBe "test-appnavn"
        currentFormatVarsel findElementsWithKey "produsertAv" shouldHaveValue "test-appnavn(test-namespace)"

        legacyFormatVarsel["tilgangstyring"].asText() shouldBe "Sikkerhetsnivå ${arkivertLegacyVarsel.sikkerhetsnivaa}"
        currentFormatVarsel findElementsWithKey "tilgangstyring" shouldHaveValue "Idporten level of assurance ${varselData.sensitivitet.name.lowercase()}"

        legacyFormatVarsel["tilleggsopplysninger"] shouldBe null
        currentFormatVarsel.apply {
            shouldHaveValue(
                "eksternVarsling.tilleggsopplysninger[0]",
                "Siste oppdatering: 26.06.2025 kl 10:05 (UTC+02:00)",
            )
            shouldHaveValue(
                "eksternVarsling.tilleggsopplysninger[1]",
                "Sendt som batch",
            )
            shouldHaveValue(
                "eksternVarsling.tilleggsopplysninger[2]",
                "Re-notifikasjon sendt",
            )
            shouldHaveValue(
                "eksternVarsling.tilleggsopplysninger[3]",
                """2 oppføringer i feilhistorikk:
                                |25.06.2025 kl 08:53 (UTC+02:00): Første feilmelding
                                |25.06.2025 kl 08:54 (UTC+02:00): Andre feilmelding
                                |----------""".trimMargin(),
            )
            shouldHaveValue(
                "eksternVarsling.tilleggsopplysninger[4]",
                "Siste status: ferdigstilt"
            )
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

        val legacyVarselAug2020 = TestVarsel(
            varselId = "legacyAug2020",
            ident = ident,
        ).withLegacyProperties(
            sikkerhetsnivaa = 4,
            forstBehandlet = "2020-08-10"
        )

        val legacyJsonFeb2023 =
            legacyVarselAug2020.deepCopy(
                varselId = "legacyFeb2023",
            ).withLegacyProperties(forstBehandlet = "2023-02-18")

        val varselJsonJan2025 = TestVarsel(
            varselId = "varselJsonJan2025",
            ident = ident,
            sensitivitet = Substantial,
            opprettet = "15-01-2025".toOsloZonedDateTime()
        )

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

        database.insertLegacyArkiverteVarsler(ident, legacyJsonFeb2023, legacyVarselAug2020)
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
        varslerMayToDec2025.varselIds shouldContainOnly listOf(
            aktivtVarselJun2025,
            aktivtVarselOct2025,
            inaktivtVarselOct2025
        ).ids
    }

    @Nested
    inner class InaktiveringsInfo {
        @Test
        fun `Håndterer varierende grad av informasjon om inaktivering`() = testVarselApi {
            val testIdent = "99876543567865"

            val inaktivBeskjedMedKildeBruker =
                TestVarsel(
                    ident = testIdent,
                    inaktivert = "01-11-2025".toOsloZonedDateTime().plusHours(10),
                    inaktivertAv = VarselInaktivertKilde.Bruker,
                    type = Beskjed,
                    aktiv = false
                ).withEksternVarsling()

            val ikkeInaktivertVarsel = TestVarsel(ident = testIdent, type = Beskjed)
            val beskjedUtenKilde =
                TestVarsel(
                    aktiv = false,
                    ident = testIdent,
                    inaktivert = "01-11-2025".toOsloZonedDateTime().plusHours(10),
                    inaktivertAv = null
                )
            val arkivertOppgaveMedKildeProdusent = inaktivBeskjedMedKildeBruker
                .deepCopy(
                    varselId = "arkivertOppgaveMedKildeProdusent",
                    type = Oppgave,
                    inaktivertAv = VarselInaktivertKilde.Produsent
                )

            val arkivertVarselMedKildeSystem = inaktivBeskjedMedKildeBruker
                .deepCopy(
                    varselId = "arkivertVarselMedKildeSystem",
                    inaktivertAv = VarselInaktivertKilde.Frist
                )
            val arkivertVarselUtenKilde = beskjedUtenKilde.deepCopy(varselId = "arkivertVarselUtenKilde")

            val legacyBeskjedMedFristUtløptTrue = arkivertOppgaveMedKildeProdusent.deepCopy(
                varselId = "legacyMedFristUtløptTrue",
                inaktivertAv = null,
                inaktivert = null,
                type = Beskjed,
                aktiv = false
            ).withLegacyProperties(utløptFrist = true)

            val legacyOppgaveMedFristUtløptFalse =
                arkivertOppgaveMedKildeProdusent.deepCopy(
                    varselId = "legacyOppgaveFristUtløptFalse",
                    aktiv = false
                ).withLegacyProperties(utløptFrist = false)

            val legacyInnboksUtenFristUtløpt =
                legacyOppgaveMedFristUtløptFalse.deepCopy(varselId = "legacyInnboksFristUtløptFalse", type = Innboks)
            val legacyBeskjedUtenFristUtløpt =
                legacyOppgaveMedFristUtløptFalse.deepCopy(varselId = "legacyBeskjedFristUtløptFalse", type = Beskjed)

            writeRepository.insertTestVarsel(
                inaktivBeskjedMedKildeBruker,
                beskjedUtenKilde,
                ikkeInaktivertVarsel
            )

            database.insertCurrentArkiverteVarsler(
                testIdent,
                arkivertOppgaveMedKildeProdusent,
                arkivertVarselUtenKilde,
                arkivertVarselMedKildeSystem,
            )
            database.insertLegacyArkiverteVarsler(
                testIdent,
                legacyBeskjedMedFristUtløptTrue,
                legacyOppgaveMedFristUtløptFalse,
                legacyInnboksUtenFristUtløpt,
                legacyBeskjedUtenFristUtløpt,
            )

            val varsler2025 =
                client.getVarslerAsJson("$endpoint?fom=2025-01-01&tom=2025-12-31", testIdent)["varsler"].toList()

            varsler2025.apply {
                size shouldBe 10
                inaktivertValue(inaktivBeskjedMedKildeBruker.varselId) shouldBe "01.11.2025 kl 10:00 (UTC+01:00) av bruker"
                inaktivertValue(beskjedUtenKilde.varselId) shouldBe "01.11.2025 kl 10:00 (UTC+01:00) av ukjent kilde"
                inaktivertValue(ikkeInaktivertVarsel.varselId) shouldBe "Ikke inaktivert"
                inaktivertValue(arkivertOppgaveMedKildeProdusent.varselId) shouldBe "01.11.2025 kl 10:00 (UTC+01:00) av produsent"
                inaktivertValue(legacyBeskjedMedFristUtløptTrue.varselId) shouldBe "av system (frist utløpt)"
                inaktivertValue(legacyOppgaveMedFristUtløptFalse.varselId) shouldBe "av produsent"
                inaktivertValue(legacyBeskjedUtenFristUtløpt.varselId) shouldBe "av bruker/produsent"
                inaktivertValue(legacyInnboksUtenFristUtløpt.varselId) shouldBe "av system"
            }
        }

        @Test
        fun `Håndterer varsler som er arkiverte men ikke inaktive`() = testVarselApi {

            val testIdent = "1234560"
            val aktivtArkivertVarsel = TestVarsel(
                opprettet = "09-08-2025".toOsloZonedDateTime(),
                varselId = "arkiverteIkkeInaktivertVarsel",
                ident = testIdent,
                sensitivitet = Sensitivitet.High,
                inaktivert = null,
                inaktivertAv = null,
                aktiv = true,
            )
            val aktivtArkivertVarselLegacy = TestVarsel(
                opprettet = "01-02-2021".toOsloZonedDateTime(),
                varselId = "arkiverteIkkeInaktivertVarselLegacy",
                ident = testIdent,
                aktiv = true,
            ).withLegacyProperties(
                sikkerhetsnivaa = 4,
                utløptFrist = false
            )

            database.insertCurrentArkiverteVarsler(testIdent, aktivtArkivertVarsel)
            database.insertLegacyArkiverteVarsler(testIdent, aktivtArkivertVarselLegacy)
            val varsler2025 =
                client.getVarslerAsJson("$endpoint?fom=2020-01-01&tom=2025-12-31", testIdent)["varsler"].toList()

            varsler2025.apply {
                inaktivertValue(aktivtArkivertVarsel.varselId) shouldBe "Ikke inaktivert"
                inaktivertValue(aktivtArkivertVarselLegacy.varselId) shouldBe "Ikke inaktivert"

            }
        }
    }

    private suspend fun HttpClient.getVarslerAsJson(path: String, ident: String): JsonNode = get(path) {
        headers.append("ident", ident)
    }.let {
        it.status shouldBe HttpStatusCode.OK
        objectMapper.readTree(it.bodyAsText())
    }

    companion object {
        private fun List<JsonNode>.inaktivertValue(id: String): String {
            return find { it["varselId"].asText() == id }.let {
                withClue("Varsel med id $id ikke funnet") {
                    it shouldNotBe null
                }
            }!!["inaktivert"].asText()
        }

        private fun String.toOsloZonedDateTime() =
            try {
                LocalDate.parse(this, DateTimeFormatter.ofPattern("dd-MM-yyyy")).atStartOfDay()
                    .atZone(ZoneId.of("Europe/Oslo"))
            } catch (ex: DateTimeParseException) {
                throw IllegalArgumentException(
                    "Failed to parse date string '$this'. Expected format is dd-MM-yyyy",
                    ex
                )
            }

        fun LocalPostgresDatabase.insertLegacyArkiverteVarsler(ident: String, vararg varsler: TestVarsel) {
            varsler.forEach {
                insertArkivertVarsel(ident, it.varselId, it.legacyJsonFormat())
            }
        }

        fun LocalPostgresDatabase.insertCurrentArkiverteVarsler(ident: String, vararg varsler: TestVarsel) {
            varsler.forEach {
                insertArkivertVarsel(ident, it.varselId, it.currentJsonFormat())
            }
        }
    }
    private fun testVarselApi(
        userIdent: String = ident,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit
    ) = baseTestApplication(
        userIdent = userIdent,
        userLoa = LevelOfAssurance.HIGH,
        readVarselRepository = readRepository,
        authentication = {
            authentication {
                tokenXMock {
                    setAsDefault = true
                }
                azureMock {
                    setAsDefault = false
                    alwaysAuthenticated = true
                }
            }
        },
        block=block
    )
}