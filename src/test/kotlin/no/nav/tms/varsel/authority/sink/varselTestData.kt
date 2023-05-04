package no.nav.tms.varsel.authority.sink

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.tms.varsel.authority.LocalDateTimeHelper
import no.nav.tms.varsel.authority.varsel.VarselType
import java.time.LocalDateTime

fun aktiverVarselEvent(
    type: VarselType,
    varselId: String,
    produsent: Produsent = Produsent("namespace", "appnavn"),
    fodselsnummer: String = "01234567890",
    tekst: String = "tekst",
    link: String = "http://link",
    sikkerhetsnivaa: Int = 4,
    forstBehandlet: LocalDateTime = LocalDateTimeHelper.nowAtUtc(),
    synligFremTil: LocalDateTime = LocalDateTimeHelper.nowAtUtc().plusDays(1),
    aktiv: Boolean = true,
    eksternVarsling: Boolean = true,
    prefererteKanaler: List<String> = listOf("SMS", "EPOST")
) = VarselEvent (
    eventName = type.eventType,
    eventId = varselId,
    namespace = produsent.namespace,
    appnavn = produsent.appnavn,
    fodselsnummer = fodselsnummer,
    tekst = tekst,
    link = link,
    sikkerhetsnivaa = sikkerhetsnivaa,
    forstBehandlet = forstBehandlet,
    synligFremTil = synligFremTil,
    aktiv = aktiv,
    eksternVarsling = eksternVarsling,
    prefererteKanaler = prefererteKanaler
)

data class VarselEvent(
    @JsonProperty("@event_name") val eventName: String,
    val eventId: String,
    val namespace: String,
    val appnavn: String,
    val fodselsnummer: String,
    val tekst: String,
    val link: String,
    val sikkerhetsnivaa: Int,
    val forstBehandlet: LocalDateTime,
    val synligFremTil: LocalDateTime,
    val aktiv: Boolean,
    val eksternVarsling: Boolean,
    val prefererteKanaler: List<String>
)

fun inaktiverVarselEvent(varselId: String) = InaktiverVarselEvent(eventId = varselId)

data class InaktiverVarselEvent(
    @JsonProperty("@event_name") val eventName: String = "done",
    val eventId: String
)
