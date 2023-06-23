package no.nav.tms.varsel.authority.database

import no.nav.tms.varsel.authority.*
import no.nav.tms.varsel.authority.common.ZonedDateTimeHelper.nowAtUtc
import no.nav.tms.varsel.authority.write.inaktiver.VarselInaktivertKilde
import java.time.ZonedDateTime
import java.util.UUID

fun dbVarsel(
    type: VarselType = VarselType.Beskjed,
    varselId: String = UUID.randomUUID().toString(),
    ident: String = "01234567890",
    aktiv: Boolean = true,
    sensitivitet: Sensitivitet = Sensitivitet.High,
    innhold: Innhold = dbInnhold(),
    produsent: Produsent = dbProdusent(),
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
    link: String? = "http://link"
) = Innhold(
    tekst = tekst,
    link = link
)

fun dbProdusent(
    namespace: String = "namespace",
    appnavn: String = "appnavn"
) = Produsent(
    namespace = namespace,
    appnavn = appnavn
)

fun dbEksternVarslingBestilling(
    prefererteKanaler: List<String> = listOf("SMS", "EPOST"),
    smsVarslingstekst: String? = "Sms-tekst",
    epostVarslingstekst: String? = "Epost-tekst",
    epostVarslingstittel: String? = "Epost-tittel"
) = EksternVarslingBestilling(
    prefererteKanaler = prefererteKanaler,
    smsVarslingstekst = smsVarslingstekst,
    epostVarslingstekst = epostVarslingstekst,
    epostVarslingstittel = epostVarslingstittel
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
