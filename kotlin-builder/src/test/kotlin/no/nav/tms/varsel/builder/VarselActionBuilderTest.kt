package no.nav.tms.varsel.builder

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.action.Varseltype.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.ZonedDateTime
import java.util.UUID

class VarselActionBuilderTest {

    private val objectMapper = jacksonObjectMapper()

    @AfterEach
    fun cleanUp() {
        BuilderEnvironment.reset()
    }

    @Test
    fun `lager opprett-event på ventet format`() {

        val testVarselId = UUID.randomUUID().toString()

        val opprettVarsel = VarselActionBuilder.opprett {
            type = Beskjed
            varselId = testVarselId
            ident = "12345678910"
            sensitivitet = Sensitivitet.High
            link = "https://link"
            tekst = Tekst("no", "tekst", default = true)
            tekster += Tekst("en", "text", default = false)
            eksternVarsling {
                kanBatches = true
            }
            aktivFremTil = ZonedDateTime.parse("2023-10-10T10:00:00Z")
            produsent = Produsent("cluster", "namespace", "app")
        }

        objectMapper.readTree(opprettVarsel).let { json ->
            json["type"].asText() shouldBe "beskjed"
            json["varselId"].asText() shouldBe testVarselId
            json["ident"].asText() shouldBe "12345678910"
            json["sensitivitet"].asText() shouldBe "high"
            json["link"].asText() shouldBe "https://link"
            json["tekster"][0].let {
                it["spraakkode"].asText() shouldBe "no"
                it["tekst"].asText() shouldBe "tekst"
                it["default"].asBoolean() shouldBe true
            }
            json["tekster"][1].let {
                it["spraakkode"].asText() shouldBe "en"
                it["tekst"].asText() shouldBe "text"
                it["default"].asBoolean() shouldBe false
            }
            json["eksternVarsling"].let {
                it.isNull shouldBe false
                it["prefererteKanaler"].size() shouldBe 0
                it["smsVarslingstekst"].shouldBeNull()
                it["epostVarslingstittel"].shouldBeNull()
                it["epostVarslingstekst"].shouldBeNull()
                it["kanBatches"].asBoolean() shouldBe true
                it["utsettSendingTil"].shouldBeNull()
            }
            json["aktivFremTil"].asText() shouldBe "2023-10-10T10:00:00Z"
            json["produsent"].let {
                it["cluster"].asText() shouldBe "cluster"
                it["namespace"].asText() shouldBe "namespace"
                it["appnavn"].asText() shouldBe "app"
            }
            json["metadata"].let {
                it["version"].asText() shouldBe VarselActionVersion
                it["built_at"].isNull shouldBe false
            }
        }
    }

    @Test
    fun `Sette riktig default verdier på eksternvarsling for oppgave`() {

        val testVarselId = UUID.randomUUID().toString()

        val opprettVarsel = VarselActionBuilder.opprett {
            type = Oppgave
            varselId = testVarselId
            ident = "12345678910"
            sensitivitet = Sensitivitet.High
            link = "https://link"
            tekst = Tekst("no", "tekst", default = true)
            eksternVarsling { kanBatches = false }
            produsent = Produsent("cluster", "namespace", "app")
        }

        objectMapper.readTree(opprettVarsel).let { json ->
            json["type"].asText() shouldBe "oppgave"
            json["eksternVarsling"].let {
                it.isNull shouldBe false
                it["utsettSendingTil"].shouldBeNull()
            }
    }
    }

    @Test
    fun `Sette riktig default verdier på eksternvarsling for innboks`() {

        val testVarselId = UUID.randomUUID().toString()

        val opprettVarsel = VarselActionBuilder.opprett {
            type = Innboks
            varselId = testVarselId
            ident = "12345678910"
            sensitivitet = Sensitivitet.High
            link = "https://link"
            tekst = Tekst("no", "tekst", default = true)
            eksternVarsling { kanBatches = true }
            produsent = Produsent("cluster", "namespace", "app")
        }

        objectMapper.readTree(opprettVarsel).let { json ->
            json["type"].asText() shouldBe "innboks"
            json["eksternVarsling"].let {
                it.isNull shouldBe false
                it["kanBatches"].asBoolean() shouldBe true
                it["utsettSendingTil"].shouldBeNull()
            }
        }
    }



    @Test
    fun `henter info om produsent automatisk for opprett-action der det er mulig`() {
        mapOf(
            "NAIS_APP_NAME" to "test-app",
            "NAIS_NAMESPACE" to "test-namespace",
            "NAIS_CLUSTER_NAME" to "dev"
        ).let { naisEnv ->
            BuilderEnvironment.extend(naisEnv)
        }

        val opprettVarsel = VarselActionBuilder.opprett {
            type = Beskjed
            varselId = UUID.randomUUID().toString()
            ident = "12345678910"
            sensitivitet = Sensitivitet.High
            link = "https://link"
            tekst = Tekst("no", "tekst", default = true)
            tekster += Tekst("en", "text", default = false)
            eksternVarsling { kanBatches = true }
            aktivFremTil = ZonedDateTime.parse("2023-10-10T10:00:00Z")
        }

        objectMapper.readTree(opprettVarsel).let { json ->
            json["produsent"].let {
                it["cluster"].asText() shouldBe "dev"
                it["namespace"].asText() shouldBe "test-namespace"
                it["appnavn"].asText() shouldBe "test-app"
            }
        }
    }

    @Test
    fun `feiler hvis produsent ikke er satt og det ikke kan hentes automatisk`() {
        shouldThrow<VarselValidationException> {
            VarselActionBuilder.opprett {
                type = Beskjed
                varselId = UUID.randomUUID().toString()
                ident = "12345678910"
                sensitivitet = Sensitivitet.High
                link = "https://link"
                tekst = Tekst("no", "tekst", default = true)
                tekster += Tekst("en", "text", default = false)
                eksternVarsling { kanBatches = true }
                aktivFremTil = ZonedDateTime.parse("2023-10-10T10:00:00Z")
            }
        }
    }

    @Test
    fun `lager inaktiver-event på ventet format`() {
        val testVarselId = UUID.randomUUID().toString()

        val inaktiverVarsel = VarselActionBuilder.inaktiver {
            varselId = testVarselId
            produsent = Produsent("cluster", "namespace", "app")
        }

        objectMapper.readTree(inaktiverVarsel).let { json ->
            json["varselId"].asText() shouldBe testVarselId
            json["produsent"].let {
                it["cluster"].asText() shouldBe "cluster"
                it["namespace"].asText() shouldBe "namespace"
                it["appnavn"].asText() shouldBe "app"
            }
            json["metadata"].let {
                it["version"].asText() shouldBe VarselActionVersion
                it["built_at"].isNull shouldBe false
            }
        }
    }

    @Test
    fun `kaster exception hvis varsel-action ikke er gyldig`() {
        shouldThrow<VarselValidationException> {
            VarselActionBuilder.opprett {
                type = Beskjed
                varselId = "badId"
                ident = "12345678910"
                sensitivitet = Sensitivitet.High
                link = "https://link"
                tekst = Tekst("no", "tekst", default = true)
                tekster += Tekst("en", "text", default = false)
                eksternVarsling { kanBatches = true }
                aktivFremTil = ZonedDateTime.parse("2023-10-10T10:00:00Z")
                produsent = Produsent("cluster", "namespace", "app")
            }
        }
    }


    @Test
    fun `henter info om produsent automatisk for inaktiver-action der det er mulig`() {
        mapOf(
            "NAIS_APP_NAME" to "test-app",
            "NAIS_NAMESPACE" to "test-namespace",
            "NAIS_CLUSTER_NAME" to "dev"
        ).let { naisEnv ->
            BuilderEnvironment.extend(naisEnv)
        }

        val opprettVarsel = VarselActionBuilder.inaktiver {
            varselId = UUID.randomUUID().toString()
        }

        objectMapper.readTree(opprettVarsel).let { json ->
            json["produsent"].let {
                it["cluster"].asText() shouldBe "dev"
                it["namespace"].asText() shouldBe "test-namespace"
                it["appnavn"].asText() shouldBe "test-app"
            }
        }
    }

    @Test
    fun `feiler for inaktiver-action hvis produsent ikke er satt og det ikke kan hentes automatisk`() {
        shouldThrow<VarselValidationException> {
            VarselActionBuilder.inaktiver {
                varselId = UUID.randomUUID().toString()
            }
        }
    }
}
