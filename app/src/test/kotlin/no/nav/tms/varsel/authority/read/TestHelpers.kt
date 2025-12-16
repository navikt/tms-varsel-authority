package no.nav.tms.varsel.authority.read

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import no.nav.tms.kafka.application.isMissingOrNull
import no.nav.tms.token.support.azure.validation.mock.azureMock
import no.nav.tms.token.support.tokenx.validation.mock.LevelOfAssurance
import no.nav.tms.token.support.tokenx.validation.mock.tokenXMock
import no.nav.tms.varsel.authority.DatabaseVarsel
import no.nav.tms.varsel.authority.Innhold
import no.nav.tms.varsel.authority.database.TestVarsel
import no.nav.tms.varsel.authority.varselApi
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktiverer
import no.nav.tms.varsel.authority.write.opprett.WriteVarselRepository
import java.text.DateFormat
import kotlin.collections.forEach

fun baseTestApplication(
    userIdent: String="1234567819",
    userLoa: LevelOfAssurance = LevelOfAssurance.HIGH,
    readVarselRepository: ReadVarselRepository,
    varselInaktiverer: VarselInaktiverer = mockk(),
    authentication: Application.() -> Unit = {
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

    },
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    application {
        varselApi(
            readVarselRepository,
            varselInaktiverer,
            installAuthenticatorsFunction = authentication
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


fun WriteVarselRepository.insertTestVarsel(vararg varsler: TestVarsel) {
    varsler.forEach { insertVarsel(it.dbVarsel()) }
}

object JsonHelpers {

    fun List<JsonNode>.shouldHaveValue(
        fieldName: String,
        expectedValue: String?,
    ) {
        shouldHaveValue(fieldName, expectedValue, JsonNode::asText)
    }

    infix fun List<JsonNode>.findElementsWithKey(key: String) = Pair(this, key)
    infix fun Pair<List<JsonNode>, String>.shouldHaveValue(expectedValue: String) {
        this.first.shouldHaveValue(this.second, expectedValue)
    }

    infix fun Pair<List<JsonNode>, String>.shouldHaveValue(expectedValue: Boolean) {
        this.first.shouldHaveValue(this.second, expectedValue, JsonNode::asBoolean)
    }

    infix fun Pair<List<JsonNode>, String>.shouldHaveValue(expectedList: List<String>) {
        this.first.shouldHaveValue(this.second, expectedList) { toList().map { it.asText() } }
    }

    private fun <T> List<JsonNode>.shouldHaveValue(
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
}

object Matchers {
    fun List<Varselsammendrag>.shouldFind(predicate: (Varselsammendrag) -> Boolean): Varselsammendrag {
        val varsel = find(predicate)

        varsel.shouldNotBeNull()

        return varsel
    }

    infix fun Varselsammendrag.shouldMatch(dbVarsel: DatabaseVarsel) {
        type shouldBe dbVarsel.type
        varselId shouldBe dbVarsel.varselId
        aktiv shouldBe dbVarsel.aktiv
        innhold shouldMatch dbVarsel.innhold
        eksternVarslingSendt shouldBe dbVarsel.eksternVarslingStatus!!.sendt
        eksternVarslingKanaler shouldBe dbVarsel.eksternVarslingStatus!!.kanaler
        opprettet shouldBe dbVarsel.opprettet
        aktivFremTil shouldBe dbVarsel.aktivFremTil
        inaktivert shouldBe dbVarsel.inaktivert
    }

    infix fun Innholdsammendrag?.shouldMatch(innhold: Innhold) {
        if (this == null) {
            return
        }

        tekst shouldBeIn (innhold.tekster.map { it.tekst } + innhold.tekst)
        spraakkode shouldBeIn (innhold.tekster.map { it.spraakkode } + "nb")
        link shouldBe innhold.link
    }

    fun List<DetaljertVarsel>.shouldFind(predicate: (DetaljertVarsel) -> Boolean): DetaljertVarsel {
        val varsel = find(predicate)
        varsel.shouldNotBeNull()
        return varsel
    }

    infix fun DetaljertVarsel.shouldMatch(dbVarsel: DatabaseVarsel) {
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

    infix fun JsonNode.shouldHaveField(string: String) {
        val existingFields = this.fields().asSequence().map { it.key }

        withClue("response should contain the field $string") {
            this[string].isMissingOrNull() shouldNotBe true
        }
    }

}


