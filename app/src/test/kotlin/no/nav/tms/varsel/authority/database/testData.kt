package no.nav.tms.varsel.authority.database

import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.action.EksternKanal.EPOST
import no.nav.tms.varsel.action.EksternKanal.SMS
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import org.intellij.lang.annotations.Language
import java.time.ZonedDateTime
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

fun legacyVarselJson(id: String, datoString: String) = """
                {
                    "link": "https://tester.test",
                    "type": "oppgave",
                    "aktiv": false,
                    "tekst": "Du må oppdatere CV-en og jobbprofilen på arbeidsplassen.no",
                    "eventId": "$id",
                    "arkivert": "2022-08-19T08:51:32.329437Z",
                    "fristUtlopt": false,
                    "produsentApp": "enSystemBruker",
                    "fodselsnummer": "10108000398",
                    "forstBehandlet": "${datoString}T11:13:55.917Z",
                    "sikkerhetsnivaa": 3,
                    "eksternVarslingSendt": false,
                    "eksternVarslingKanaler": []
                  }
            """.trimIndent()
