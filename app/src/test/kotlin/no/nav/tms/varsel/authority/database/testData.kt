package no.nav.tms.varsel.authority.database

import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.action.EksternKanal.EPOST
import no.nav.tms.varsel.action.EksternKanal.SMS
import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
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
    historikk: List<EksternVarslingHistorikkEntry> = dbEksternVarslingHistorikk(),
    sistOppdatert: ZonedDateTime = nowAtUtc()
) = EksternVarslingStatus(
    sendt = sendt,
    renotifikasjonSendt = renotifikasjonSendt,
    kanaler = kanaler,
    historikk = historikk,
    sistOppdatert = sistOppdatert
)

fun dbEksternVarslingHistorikk() = listOf(
    dbHistorikkEntry(),
    dbHistorikkEntry(
        melding = "Sendt på sms",
        status = EksternStatus.Sendt,
        distribusjonsId = 1L,
        kanal = "SMS",
        renotifikasjon = false
    ),
    dbHistorikkEntry(
        melding = "Sendt på epost",
        status = EksternStatus.Sendt,
        distribusjonsId = 2L,
        kanal = "EPOST",
        renotifikasjon = false
    ),
    dbHistorikkEntry(
        melding = "Sendt på sms",
        status = EksternStatus.Ferdigstilt,
    )
)

fun dbHistorikkEntry(
    melding: String = "Oversendt",
    status: EksternStatus = EksternStatus.Bestilt,
    distribusjonsId: Long? = null,
    kanal: String? = null,
    renotifikasjon: Boolean? = null,
    tidspunkt: ZonedDateTime = nowAtUtc()
) = EksternVarslingHistorikkEntry(
    melding = melding,
    status = status,
    distribusjonsId = distribusjonsId,
    kanal = kanal,
    renotifikasjon = renotifikasjon,
    tidspunkt = tidspunkt
)
