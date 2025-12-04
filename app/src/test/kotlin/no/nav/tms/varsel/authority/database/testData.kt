package no.nav.tms.varsel.authority.database

import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.action.EksternKanal.EPOST
import no.nav.tms.varsel.action.EksternKanal.SMS
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID


fun dbVarsel(
    type: Varseltype = Varseltype.Beskjed,
    varselId: String = UUID.randomUUID().toString(),
    ident: String = "01234567890",
    aktiv: Boolean = true,
    sensitivitet: Sensitivitet = Sensitivitet.High,
    innhold: Innhold = dbInnhold(),
    produsent: DatabaseProdusent = dbProdusent(),
    eksternVarslingBestilling: EksternVarslingBestilling? = dbEksternVarslingBestilling(),
    eksternVarslingStatus: EksternVarslingStatus? = dbEksternVarslingStatus(),
    opprettet: ZonedDateTime = nowAtUtc(),
    aktivFremTil: ZonedDateTime? = nowAtUtc().plusDays(7),
    inaktivert: ZonedDateTime? = null,
    inaktivertAv: VarselInaktivertKilde? = null
) = DatabaseVarsel(
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
    inaktivertAv = inaktivertAv,
)

fun dbInnhold(
    tekst: String = "varseltekst",
    link: String? = "http://link",
    tekster: List<Tekst> = listOf(Tekst("nb", "varseltekst", false))
) = Innhold(
    tekst = tekst,
    link = link,
    tekster = tekster
)

fun dbProdusent(
    cluster: String? = "cluster",
    namespace: String = "namespace",
    appnavn: String = "appnavn"
) = DatabaseProdusent(
    cluster = cluster,
    namespace = namespace,
    appnavn = appnavn
)

fun dbEksternVarslingBestilling(
    prefererteKanaler: List<EksternKanal> = listOf(SMS, EPOST),
    smsVarslingstekst: String? = "Sms-tekst",
    epostVarslingstittel: String? = "Epost-tittel",
    epostVarslingstekst: String? = "Epost-tekst"
) = EksternVarslingBestilling(
    prefererteKanaler = prefererteKanaler,
    smsVarslingstekst = smsVarslingstekst,
    epostVarslingstittel = epostVarslingstittel,
    epostVarslingstekst = epostVarslingstekst
)

fun dbEksternVarslingStatus(
    sendt: Boolean = true,
    renotifikasjonSendt: Boolean = false,
    kanaler: List<String> = listOf("SMS", "EPOST"),
    sistOppdatert: ZonedDateTime = nowAtUtc()
) = EksternVarslingStatus(
    sendt = sendt,
    renotifikasjonSendt = renotifikasjonSendt,
    kanaler = kanaler,
    sistOppdatert = sistOppdatert
)

data class ArkiverteDbVarsel(
    val ident: String,
    val link: String? = "https://tester.test",
    val tekst: String = "Dette er ett testvarsel",
    val type: String = "oppgave",
    val id: String = UUID.randomUUID().toString(),
    val arkivertDato: ZonedDateTime = nowAtUtc(),
    val produsentApp: String = "testapp",
    val eksternVarslingSendt: Boolean = false,
    val eksternVarslingKanaler: List<String> = listOf("SMS", "EPOST"),
    var confidentilality: Confidentiality,
    var sendSomBatch: Boolean = false,
    var opprettet: ZonedDateTime = nowAtUtc().minusYears(1),
    var renotifikasjonSendt: Boolean? = null,
    var forstBehandlet: ZonedDateTime? = null,
    var inaktiverDato: ZonedDateTime? = null,
    var inaktivertAv: VarselInaktivertKilde? = null,
    var deaktivertPgaUtløptFrist: Boolean? = null,
    var nameSpace: String? = null,
    var feilhistorikk: List<FeilhistorikkEntry>? = null,
    var eksternVarslingSistOppdatert: ZonedDateTime? = null,
    var aktiv: Boolean = false,
) {

    private var eksternVarslingSisteStatus: String = "ferdigstilt"

    private val serializedKanalList = eksternVarslingKanaler.joinToString(
        prefix = "[",
        postfix = "]",
        separator = ","
    ) { "\"$it\"" }

    fun legacyJsonFormat(): String {
        require(deaktivertPgaUtløptFrist != null) { "deaktivertPgaUtløptFrist må være satt før legacy json kan genereres" }
        forstBehandlet = forstBehandlet.takeIf { it != null } ?: opprettet
        return """
                {
                    "link": "$link",
                    "type": "$type",
                    "aktiv": $aktiv,
                    "tekst": "$tekst",
                    "eventId": "$id",
                    "arkivert": "${arkivertDato.serializeToLegacyDbFormat()}",
                    "fristUtlopt": $deaktivertPgaUtløptFrist,
                    "produsentApp": "$produsentApp",
                    "fodselsnummer": "$ident",
                    "forstBehandlet": "${forstBehandlet!!.serializeToLegacyDbFormat()}",
                    "sikkerhetsnivaa": ${confidentilality.sikkerhetsnivaa},
                    "eksternVarslingSendt": $eksternVarslingSendt,
                    "eksternVarslingKanaler": $serializedKanalList
                  }
            """.trimIndent()
    }


    fun currentJsonFormat(): String {
        require(nameSpace != null) { "nameSpace må være satt før current json kan genereres" }
        require(renotifikasjonSendt != null) { "renotifikasjonSendt må være satt før current json kan genereres" }

        return """
                  {
                    "type": "$type",
                    "aktiv": $aktiv,
                    "ident": "$ident",
                    "innhold": {
                      "link": "$link",
                      "tekst": "$tekst",
                      "tekster": [
                        {
                          "tekst": "$tekst",
                          "default": true,
                          "spraakkode": "nb"
                        }
                      ]
                    },
                    "varselId": "$id",
                    "opprettet": "${opprettet.serializeToDbFormat()}",
                    "produsent": {
                      "appnavn": "$produsentApp",
                      "cluster": "test-cluster",
                      "namespace": "$nameSpace"
                    },
                    "inaktivert": ${inaktiverDato?.let { "\"${it.serializeToDbFormat()}\"" }},
                    "inaktivertAv": ${inaktivertAv?.let { "\"${it.name}\"" }},
                    "sensitivitet": "${confidentilality.loa}",
                    "eksternVarslingStatus": {
                      "sendt": $eksternVarslingSendt,
                      "kanaler": $serializedKanalList,
                      "sendtSomBatch": $sendSomBatch,
                      "sisteStatus": "$eksternVarslingSisteStatus",
                      "sistOppdatert": ${
            eksternVarslingSistOppdatert?.serializeToDbFormat()?.let { "\"$it\"" } ?: "null"
        },
                      "renotifikasjonSendt": $renotifikasjonSendt,
                      "feilhistorikk": ${serializeFeilhistorikk()}
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
    }

    private fun serializeFeilhistorikk(): String =
        feilhistorikk?.joinToString(prefix = "[", postfix = "]", separator = ",") {
            """
                {
                    "tidspunkt": "${it.tidspunkt.serializeToDbFormat()}",
                    "feilmelding": "${it.feilmelding}"
                }
            """.trimIndent()
        } ?: "[]"

    fun withLegacyProperties(
        forstBehandletStr: String? = null,
        forstBehandlet: ZonedDateTime = nowAtUtc().minusYears(1),
        deaktivertPgaUtløptFrist: Boolean? = false
    ) = apply {
        this.forstBehandlet = forstBehandletStr?.toZonedDateTimeUtc() ?: forstBehandlet
        this.deaktivertPgaUtløptFrist = deaktivertPgaUtløptFrist
    }


    fun withCurrentProperties(
        inaktiverDato: ZonedDateTime = opprettet.plusDays(1),
        inaktivertAv: VarselInaktivertKilde = VarselInaktivertKilde.Frist,
        nameSpace: String = "test-namespace",
        renotifikasjonSendt: Boolean = false,
        feilhistorikk: List<FeilhistorikkEntry> = emptyList(),
        sistOppdatert: ZonedDateTime? = null,
        sisteStatus: String? = null
    ) = apply {
        this.inaktiverDato = inaktiverDato
        this.inaktivertAv = inaktivertAv
        this.nameSpace = nameSpace
        this.renotifikasjonSendt = renotifikasjonSendt
        this.feilhistorikk = feilhistorikk
        this.eksternVarslingSistOppdatert = sistOppdatert
        this.eksternVarslingSisteStatus = sisteStatus ?: this.eksternVarslingSisteStatus
    }

    companion object {

        fun String.toZonedDateTimeUtc(hour: Int = 9): ZonedDateTime =
            LocalDate.parse(this)
                .atTime(hour, 0)
                .atZone(ZoneOffset.UTC)

        fun ZonedDateTime.serializeToDbFormat(): String? =
            this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"))

        fun ZonedDateTime.serializeToLegacyDbFormat(): String? =
            this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

        fun generateFromDatabaseVarsel(
            varsel: DatabaseVarsel,
            id: String = UUID.randomUUID().toString(),
        ) =
            ArkiverteDbVarsel(
                id = id,
                ident = varsel.ident,
                link = varsel.innhold.link!!,
                tekst = varsel.innhold.tekst,
                type = varsel.type.name,
                arkivertDato = nowAtUtc(),
                produsentApp = varsel.produsent.appnavn,
                eksternVarslingSendt = varsel.eksternVarslingStatus?.sendt!!,
                eksternVarslingKanaler = varsel.eksternVarslingStatus.kanaler,
                confidentilality = Confidentiality.convertFromLoa(varsel.sensitivitet),
                sendSomBatch = varsel.eksternVarslingStatus.sendtSomBatch,
                opprettet = varsel.opprettet,
                renotifikasjonSendt = varsel.eksternVarslingStatus.renotifikasjonSendt,
                forstBehandlet = varsel.opprettet,
                inaktiverDato = varsel.inaktivert,
                inaktivertAv = varsel.inaktivertAv,
                aktiv = varsel.inaktivert != null,
                deaktivertPgaUtløptFrist = varsel.inaktivertAv == VarselInaktivertKilde.Frist,
                nameSpace = varsel.produsent.namespace,
                feilhistorikk = varsel.eksternVarslingStatus.feilhistorikk.map {
                    FeilhistorikkEntry(
                        tidspunkt = it.tidspunkt,
                        feilmelding = it.feilmelding
                    )
                }
            )
    }

    enum class Confidentiality(val sikkerhetsnivaa: Int, val loa: String) {
        LEVEL3(3, "substantial"),
        LEVEL4(4, "high"),
        HIGH(4, "high"),
        SUBSTANTIAL(3, "substantial");

        companion object {
            fun convertFromLoa(loa: Sensitivitet): Confidentiality = when (loa.name.lowercase()) {
                "high" -> LEVEL4
                "substantial" -> LEVEL3
                else -> throw IllegalArgumentException("Ukjent loa-nivå: $loa")
            }
        }
    }

    class FeilhistorikkEntry(
        val tidspunkt: ZonedDateTime,
        val feilmelding: String
    )
}


