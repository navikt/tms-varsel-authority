package no.nav.tms.varsel.authority.database
import com.fasterxml.jackson.databind.JsonNode
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID


data class TestVarsel(
    val type: Varseltype = Varseltype.Beskjed,
    val varselId: String = UUID.randomUUID().toString(),
    val ident: String = "01234567890",
    val aktiv: Boolean = true,
    val sensitivitet: Sensitivitet = Sensitivitet.High,
    val innhold: Innhold = testInnhold(),
    var produsent: DatabaseProdusent = DatabaseProdusent(
        cluster = "testcluster",
        namespace = "testnamespace",
        appnavn = "testappnavn"
    ),
    var opprettet: ZonedDateTime = nowInOsloUtc().minusDays(1),
    val aktivFremTil: ZonedDateTime? = nowAtUtc().plusDays(7),
    var inaktivert: ZonedDateTime? = null,
    var inaktivertAv: VarselInaktivertKilde? = null,
) {

    var arkivertDato: ZonedDateTime? = null
    var deaktivertPgaUtløptFrist: Boolean? = null
    var eksternVarslingBestilling: EksternVarslingBestilling? = null
    var eksternVarslingStatus: EksternVarslingStatus? = null

    val produsentApp: String
        get() = produsent.appnavn

    val forstBehandlet: ZonedDateTime
        get() = opprettet
    val sikkerhetsnivaa: Int
        get() = when (sensitivitet) {
            Sensitivitet.Substantial -> 3
            Sensitivitet.High -> 4
        }

    private fun serializedKanalList() = eksternVarslingStatus?.kanaler?.jsonList() ?: "[]"

    fun dbVarsel(withEksternVarsling: Boolean = true): DatabaseVarsel {
        if (withEksternVarsling) {
            withEksternVarsling()
        }
        return DatabaseVarsel(
            type = type,
            varselId = varselId,
            ident = ident,
            aktiv = aktiv,
            sensitivitet = sensitivitet,
            innhold = innhold,
            produsent = produsent,
            eksternVarslingBestilling = eksternVarslingBestilling,
            eksternVarslingStatus = eksternVarslingStatus,
            opprettet = opprettet,
            aktivFremTil = aktivFremTil,
            inaktivert = inaktivert,
            inaktivertAv = inaktivertAv
        )
    }

    fun legacyJsonFormat(): String {
        require(deaktivertPgaUtløptFrist != null) { "deaktivertPgaUtløptFrist må være satt før legacy json kan genereres" }
        require(arkivertDato != null) { "arkivertDato må være satt for å produsere arkivjson" }
        return """
            {
                "link": "${innhold.link}",
                "type": "${type.name}",
                "aktiv": $aktiv,
                "tekst": "${innhold.tekst}",
                "eventId": "$varselId",
                "arkivert": "${arkivertDato!!.serializeToLegacyDbFormat()}",
                "fristUtlopt": $deaktivertPgaUtløptFrist,
                "produsentApp": "$produsentApp",
                "fodselsnummer": "$ident",
                "forstBehandlet": "${forstBehandlet.serializeToLegacyDbFormat()}",
                "sikkerhetsnivaa": ${sikkerhetsnivaa},
                "eksternVarslingSendt": ${eksternVarslingStatus?.sendt ?: false},
                "eksternVarslingKanaler": ${serializedKanalList()}
              }
        """.trimIndent()
    }

    private fun Innhold.serializeForArchive() = """
        {
                      "link": "$link",
                      "tekst": "$tekst",
                      "tekster": ${
        tekster.jsonList {
            """       
                          {
                              "tekst": "${it.tekst}",
                              "default": ${it.default},
                              "spraakkode": "${it.spraakkode}"
                          } """.trimIndent()
        }
    }
        }
    """.trimIndent()

    fun currentJsonFormat(): String {
        if (inaktivertAv != null && inaktivert == null) {
            inaktivert = opprettet.plusHours(6)
        }
        return """
                  {
                    "type": "$type",
                    "aktiv": $aktiv,
                    "ident": "$ident",
                    "innhold": ${innhold.serializeForArchive()},
                    "varselId": "$varselId",
                    "opprettet": "${opprettet.serializeToDbFormat()}",
                    "produsent": {
                      "appnavn": "$produsentApp",
                      "cluster": "test-cluster",
                      "namespace": "${produsent.namespace}"
                    },
                    "inaktivert": ${inaktivert?.let { "\"${it.serializeToDbFormat()}\"" }},
                    "inaktivertAv": ${inaktivertAv?.let { "\"${it.name}\"" }},
                    "sensitivitet": "${sensitivitet.name.lowercase()}",
                    "eksternVarslingStatus": ${eksternVarslingStatus?.serializeForArchive()},
                    "eksternVarslingBestilling": ${eksternVarslingBestilling?.serializeForArchive()}
                  }
            """.trimIndent()
    }

    fun withLegacyProperties(
        arkivertDato: ZonedDateTime? = null,
        utløptFrist: Boolean? = null,
        sikkerhetsnivaa: Int? = null,
        forstBehandlet: String? = null,
    ): TestVarsel {
        val nyArkivertDato = arkivertDato ?: this.arkivertDato ?: opprettet.plusDays(90)
        val nyUtløptFrist = utløptFrist ?: this.deaktivertPgaUtløptFrist ?: false
        val nySensitivitet = when (sikkerhetsnivaa) {
            3 -> Sensitivitet.Substantial
            4 -> Sensitivitet.High
            null -> this.sensitivitet
            else -> throw IllegalArgumentException("Ugyldig sikkerhetsnivaa: $sikkerhetsnivaa")
        }
        val nyOpprettetDato = forstBehandlet?.toZonedDateTimeUtc() ?: this.opprettet

        return this.deepCopy(
            sensitivitet = nySensitivitet,
            opprettet = nyOpprettetDato
        ).apply {
            this.arkivertDato = nyArkivertDato
            deaktivertPgaUtløptFrist = nyUtløptFrist
        }
    }


    fun withEksternVarsling() = apply {
        if (eksternVarslingStatus == null)
            eksternVarslingStatus = defaultEksternVarslingStatus
        if (eksternVarslingBestilling == null)
            eksternVarslingBestilling = defaultEksternVarslingBestilling
    }

    fun deepCopy(
        varselId: String = UUID.randomUUID().toString(),
        type: Varseltype = this.type,
        ident: String = this.ident,
        aktiv: Boolean = this.aktiv,
        sensitivitet: Sensitivitet = this.sensitivitet,
        innhold: Innhold = this.innhold.copy(
            tekst = this.innhold.tekst,
            link = this.innhold.link,
            tekster = this.innhold.tekster.map { it.copy() }
        ),
        produsent: DatabaseProdusent = this.produsent.copy(),
        opprettet: ZonedDateTime = this.opprettet,
        aktivFremTil: ZonedDateTime? = this.aktivFremTil,
        inaktivert: ZonedDateTime? = this.inaktivert,
        inaktivertAv: VarselInaktivertKilde? = this.inaktivertAv
    ): TestVarsel {
        return TestVarsel(
            type = type,
            varselId = varselId,
            ident = ident,
            aktiv = aktiv,
            sensitivitet = sensitivitet,
            innhold = innhold,
            produsent = produsent,
            opprettet = opprettet,
            aktivFremTil = aktivFremTil,
            inaktivert = inaktivert,
            inaktivertAv = inaktivertAv
        ).apply {
            arkivertDato = this@TestVarsel.arkivertDato
            deaktivertPgaUtløptFrist = this@TestVarsel.deaktivertPgaUtløptFrist
            eksternVarslingBestilling = this@TestVarsel.eksternVarslingBestilling?.copy(
                prefererteKanaler = this@TestVarsel.eksternVarslingBestilling?.prefererteKanaler?.toList()
                    ?: emptyList(),
                smsVarslingstekst = this@TestVarsel.eksternVarslingBestilling?.smsVarslingstekst,
                epostVarslingstittel = this@TestVarsel.eksternVarslingBestilling?.epostVarslingstittel,
                epostVarslingstekst = this@TestVarsel.eksternVarslingBestilling?.epostVarslingstekst
            )
            eksternVarslingStatus = this@TestVarsel.eksternVarslingStatus?.copy(
                sendt = this@TestVarsel.eksternVarslingStatus?.sendt ?: false,
                sendtSomBatch = this@TestVarsel.eksternVarslingStatus?.sendtSomBatch ?: false,
                renotifikasjonSendt = this@TestVarsel.eksternVarslingStatus?.renotifikasjonSendt ?: false,
                kanaler = this@TestVarsel.eksternVarslingStatus?.kanaler?.toList() ?: emptyList(),
                feilhistorikk = this@TestVarsel.eksternVarslingStatus?.feilhistorikk?.map { it.copy() }
                    ?: emptyList(),
                sisteStatus = this@TestVarsel.eksternVarslingStatus?.sisteStatus,
                sistOppdatert = this@TestVarsel.eksternVarslingStatus!!.sistOppdatert
            )
        }
    }

    companion object {
        private val defaultEksternVarslingBestilling = EksternVarslingBestilling(
            prefererteKanaler = listOf(EksternKanal.SMS),
            smsVarslingstekst = "Dette er en SMS varseltekst",
            epostVarslingstittel = "Dette er en epost varsel tittel",
            epostVarslingstekst = "Dette er en epost varseltekst"
        )

        private val defaultEksternVarslingStatus = EksternVarslingStatus(
            sendt = true,
            sendtSomBatch = false,
            renotifikasjonSendt = false,
            kanaler = listOf("SMS"),
            feilhistorikk = emptyList(),
            sisteStatus = EksternStatus.Sendt,
            sistOppdatert = nowInOsloUtc()
        )

        val List<TestVarsel>.ids
            get() = map { it.varselId }
        val List<JsonNode>.varselIds
            get() = map { it["varselId"].asText() }
    }
}

private fun <T> List<T>.jsonList(stringProducer: (T) -> String = { "\"$it\"" }) = joinToString(
    prefix = "[",
    postfix = "]",
    separator = ","
) { stringProducer(it) }

private fun EksternVarslingStatus.serializeForArchive() =
    """{
      "sendt": $sendt,
      "kanaler": ${kanaler.jsonList()},
      "sendtSomBatch": ${sendtSomBatch},
      "sisteStatus": ${sisteStatus?.let { "\"${it.name.lowercase()}\"" }},
      "sistOppdatert": ${sistOppdatert.serializeToDbFormat().let { "\"$it\"" }},
      "renotifikasjonSendt": $renotifikasjonSendt,
      "feilhistorikk": ${
        feilhistorikk.jsonList {
            """{
                "tidspunkt": "${it.tidspunkt.serializeToDbFormat()}",
                "feilmelding": "${it.feilmelding}"
                } """.trimIndent()
        }
    }
     }
    """.trimIndent()

private fun EksternVarslingBestilling.serializeForArchive() = """
        {
                      "kanaler": ${prefererteKanaler.jsonList { "\"${it.name}\"" }},
                      "smsVarslingstekst": "$smsVarslingstekst",
                      "epostVarslingstittel": "$epostVarslingstittel",
                      "epostVarslingstekst": "$epostVarslingstekst"
                    }
    """.trimIndent()

fun testInnhold(
    tekst: String = "varseltekst",
    link: String? = "http://link",
    tekster: List<Tekst> = listOf(Tekst("nb", "varseltekst", false))
) = Innhold(
    tekst = tekst,
    link = link,
    tekster = tekster
)

private fun ZonedDateTime.serializeToDbFormat(): String =
    this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))

private fun ZonedDateTime.serializeToLegacyDbFormat(): String =
    this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

private fun String.toZonedDateTimeUtc(hour: Int = 9): ZonedDateTime =
    LocalDate.parse(this).atTime(hour, 0).atZone(ZoneOffset.UTC)

private fun nowInOsloUtc(): ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS)
